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

from pathlib import Path
import unittest
from pyfakefs import fake_filesystem_unittest
from tools.asuite.atest import java_test_filter_generator


class JavaTestFilterGeneratorTest(fake_filesystem_unittest.TestCase):
  """Unit tests for java_test_filter_generator.py"""

  def setUp(self):
    self.setUpPyfakefs()
    self.class_files = []

    test_a = 'TestA.java'
    self.fs.create_file(
        test_a, contents='package android.test;\npublic class TestA {\n'
    )
    self.class_files.append(test_a)

    test_b = 'TestB.java'
    self.fs.create_file(
        test_b,
        contents=(
            'package android.test;\n'
            '@RunWith(Parameterized.class)\n'
            'public class TestB {\n'
        ),
    )
    self.class_files.append(test_b)

    test_c = 'TestC.kt'
    self.fs.create_file(
        test_c, contents='package android.test\nclass TestC : TestBase() {\n'
    )
    self.class_files.append(test_c)

    test_d = 'TestD.kt'
    self.fs.create_file(
        test_d,
        contents=(
            'package android.test\n'
            '@RunWith(Parameterized::class)\n'
            'class TestD : TestBase() {\n'
        ),
    )
    self.class_files.append(test_d)

  def test_get_test_filters_class_name_no_method(self):
    filters = java_test_filter_generator._get_test_filters(
        ['TestB', 'TestD', 'TestNotExist'], self.class_files
    )

    self.assertCountEqual(filters, ['android.test.TestB', 'android.test.TestD'])

  def test_get_test_filters_full_class_name_no_method(self):
    filters = java_test_filter_generator._get_test_filters(
        [
            'android.test.TestA',
            'android.test.TestC',
            'android.test.wrong.TestD',
        ],
        self.class_files,
    )

    self.assertCountEqual(filters, ['android.test.TestA', 'android.test.TestC'])

  def test_get_test_filters_class_name_with_methods(self):
    filters = java_test_filter_generator._get_test_filters(
        [
            'TestA#method1',
            'TestB#method1,method2,method3[0]',
            'TestC#method1,method2',
            'TestD#method1,method2,method3[0]',
        ],
        self.class_files,
    )

    self.assertCountEqual(
        filters,
        [
            'android.test.TestA#method1',
            'android.test.TestB#method1*',
            'android.test.TestB#method2*',
            'android.test.TestB#method3[0]',
            'android.test.TestC#method1',
            'android.test.TestC#method2',
            'android.test.TestD#method1*',
            'android.test.TestD#method2*',
            'android.test.TestD#method3[0]',
        ],
    )


if __name__ == '__main__':
  unittest.main(verbosity=2)
