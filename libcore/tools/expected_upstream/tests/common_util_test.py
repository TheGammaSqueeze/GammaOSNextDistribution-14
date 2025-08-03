#!/usr/bin/python3 -B

# Copyright 2021 The Android Open Source Project
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
"""Tests for common_util."""

from typing import List
import unittest

# pylint: disable=g-multiple-import
from common_util import (
    LIBCORE_DIR,
    OpenjdkFinder,
    OjluniFinder,
)

from git import Repo


class OjluniFinderTest(unittest.TestCase):

  LIST_OJLUNI_PATHS = [
      'ojluni/src/main/java/java/lang/String.java',
      'ojluni/src/main/java/java/lang/StringBuilder.java',
      'ojluni/src/main/java/java/lang/package-info.java',
      'ojluni/src/main/java/java/math/BigInteger.java',
      'ojluni/src/test/java/math/BigInteger/BigIntegerTest.java',
      'ojluni/src/main/native/System.c',
  ]

  def setUp(self):
    super().setUp()
    self.ojluni_finder = OjluniFinder(self.LIST_OJLUNI_PATHS)

  def test_translate_from_class_name_to_ojluni_path(self):
    # Translates a package
    self.assert_class_to_path(
        'java.lang.NullPointerException',
        'ojluni/src/main/java/java/lang/NullPointerException.java')
    self.assert_class_to_path(
        'TopPackage.SubPackage.ClassA',
        'ojluni/src/main/java/TopPackage/SubPackage/ClassA.java')

    # Translates a test package
    self.assert_class_to_path(
        'test.java.lang.NullPointerException',
        'ojluni/src/test/java/lang/NullPointerException.java')
    self.assert_class_to_path(
        'test.TopPackage.SubPackage.ClassA',
        'ojluni/src/test/TopPackage/SubPackage/ClassA.java')

    # Translates a path
    self.assert_class_to_path(
        'ojluni/src/main/java/java/lang/NullPointerException.java',
        'ojluni/src/main/java/java/lang/NullPointerException.java')

    # Test edge cases
    self.assert_class_to_path('a', 'ojluni/src/main/java/a.java')
    self.assert_class_to_path('test.a', 'ojluni/src/test/a.java')

  def assert_class_to_path(self, classname: str, expected_path: str):
    translated_path = OjluniFinder.translate_from_class_name_to_ojluni_path(
        classname)
    self.assertEqual(translated_path, expected_path)

  def test_ojluni_match_path_prefix(self):
    # directory path ending with / or not has different results
    self.assert_match_ojluni_path('ojluni/src/main/java/java', [
        'ojluni/src/main/java/java/',
    ])
    self.assert_match_ojluni_path('ojluni/src/main/java/java/', [
        'ojluni/src/main/java/java/lang/',
        'ojluni/src/main/java/java/math/',
    ])

    self.assert_match_ojluni_path('ojluni/src/main/java/java/lang/', [
        'ojluni/src/main/java/java/lang/String.java',
        'ojluni/src/main/java/java/lang/StringBuilder.java',
        'ojluni/src/main/java/java/lang/package-info.java',
    ])
    self.assert_match_ojluni_path('ojluni/src/main/java/java/lang/String', [
        'ojluni/src/main/java/java/lang/String.java',
        'ojluni/src/main/java/java/lang/StringBuilder.java',
    ])
    self.assert_match_ojluni_path('ojluni/src/main/java/java/lang/StringB', [
        'ojluni/src/main/java/java/lang/StringBuilder.java',
    ])
    self.assert_match_ojluni_path('ojluni/src/main/java/java/math/', [
        'ojluni/src/main/java/java/math/BigInteger.java',
    ])

    self.assert_match_ojluni_path('ojluni/src/test/java/math/BigInteger/', [
        'ojluni/src/test/java/math/BigInteger/BigIntegerTest.java',
    ])
    self.assert_match_ojluni_path('ojluni/src/test/java/math/BigInteger/', [
        'ojluni/src/test/java/math/BigInteger/BigIntegerTest.java',
    ])

  def assert_match_ojluni_path(self, path_prefix: str, expected: List[str]):
    result = self.ojluni_finder.match_path_prefix(path_prefix)
    self.assertEqual(set(expected), result)

  def test_ojluni_match_classname_prefix(self):
    # directory path ending with / or not has different results
    self.assert_match_ojluni_classname('java', ['java.'])
    self.assert_match_ojluni_classname('java.', [
        'java.lang.',
        'java.math.',
    ])

    self.assert_match_ojluni_classname('java.lang.', [
        'java.lang.String',
        'java.lang.StringBuilder',
        'java.lang.package-info',
    ])
    self.assert_match_ojluni_classname('java.lang.String', [
        'java.lang.String',
        'java.lang.StringBuilder',
    ])
    self.assert_match_ojluni_classname('java.lang.StringB', [
        'java.lang.StringBuilder',
    ])

    self.assert_match_ojluni_classname('java.math.', [
        'java.math.BigInteger',
    ])
    self.assert_match_ojluni_classname('java.math.BigInteger', [
        'java.math.BigInteger',
    ])
    self.assert_match_ojluni_classname('test.java.math.BigInteger.', [
        'test.java.math.BigInteger.BigIntegerTest',
    ])

  def assert_match_ojluni_classname(self, path_prefix: str,
                                    expected: List[str]):
    result = self.ojluni_finder.match_classname_prefix(path_prefix)
    self.assertEqual(set(expected), set(result))


class OpenJdkFinderTest(unittest.TestCase):

  def setUp(self):
    super().setUp()
    self.repo = Repo(LIBCORE_DIR.as_posix())
    commit = self.repo.commit('jdk11u/jdk-11.0.13-ga')
    self.finder = OpenjdkFinder(commit)

  def tearDown(self):
    super().tearDown()
    self.repo.close()

  def test_translate_src_path_to_ojluni_path(self):
    self.assertEqual(
        'ojluni/src/main/java/java/lang/String.java',
        OpenjdkFinder.translate_src_path_to_ojluni_path(
            'src/java.base/share/classes/java/lang/String.java'))
    self.assertEqual(
        'ojluni/src/test/java/math/BigInteger/BigIntegerTest.java',
        OpenjdkFinder.translate_src_path_to_ojluni_path(
            'test/jdk/java/math/BigInteger/BigIntegerTest.java'))

  def test_find_src_path_from_classname(self):
    self.assertEqual(
        'src/java.base/share/classes/java/lang/String.java',
        self.finder.find_src_path_from_classname('java.lang.String'))
    self.assertEqual(
        'test/jdk/java/math/BigInteger/BigIntegerTest.java',
        self.finder.find_src_path_from_classname(
            'java.math.BigInteger.BigIntegerTest'))

  def test_find_src_path_from_ojluni_path(self):
    self.assertEqual(
        'src/java.base/share/classes/java/lang/String.java',
        self.finder.find_src_path_from_ojluni_path(
            'ojluni/src/main/java/java/lang/String.java'))
    self.assertEqual(
        'test/jdk/java/math/BigInteger/BigIntegerTest.java',
        self.finder.find_src_path_from_ojluni_path(
            'ojluni/src/test/java/math/BigInteger/BigIntegerTest.java'))
    self.assertEqual(
        'src/java.base/unix/classes/sun/nio/fs/UnixPath.java',
        self.finder.find_src_path_from_ojluni_path(
            'ojluni/src/main/java/sun/nio/fs/UnixPath.java'))
    self.assertEqual(
        'src/java.sql/share/classes/java/sql/Array.java',
        self.finder.find_src_path_from_ojluni_path(
            'ojluni/src/main/java/java/sql/Array.java'))
    self.assertEqual(
        'src/java.logging/share/classes/java/util/logging/Formatter.java',
        self.finder.find_src_path_from_ojluni_path(
            'ojluni/src/main/java/java/util/logging/Formatter.java'))

  def test_match_path_prefix(self):
    self.assert_match_path_prefix([
        'src/java.base/share/classes/java/',
        'src/java.base/share/classes/javax/',
    ], 'src/java.base/share/classes/java')
    self.assert_match_path_prefix([
        'src/java.base/share/classes/java/io/',
        'src/java.base/share/classes/java/lang/',
        'src/java.base/share/classes/java/math/',
        'src/java.base/share/classes/java/net/',
        'src/java.base/share/classes/java/nio/',
        'src/java.base/share/classes/java/security/',
        'src/java.base/share/classes/java/text/',
        'src/java.base/share/classes/java/time/',
        'src/java.base/share/classes/java/util/',
    ], 'src/java.base/share/classes/java/')
    self.assert_match_path_prefix([
        'src/java.base/share/classes/java/lang/StringBuffer.java',
        'src/java.base/share/classes/java/lang/StringIndexOutOfBoundsException.java',
        'src/java.base/share/classes/java/lang/StringUTF16.java',
        'src/java.base/share/classes/java/lang/String.java',
        'src/java.base/share/classes/java/lang/StringConcatHelper.java',
        'src/java.base/share/classes/java/lang/StringLatin1.java',
        'src/java.base/share/classes/java/lang/StringBuilder.java',
        'src/java.base/share/classes/java/lang/StringCoding.java',
    ], 'src/java.base/share/classes/java/lang/String')
    self.assert_match_path_prefix([
        'test/jdk/java/math/BigInteger/BigIntegerTest.java',
    ], 'test/jdk/java/math/BigInteger/BigInteger')

  def assert_match_path_prefix(self, expected: List[str], prefix: str):
    result = self.finder.match_path_prefix(prefix)
    self.assertEqual(set(expected), set(result))

  def test_match_classname_prefix(self):
    self.assert_match_classname_prefix([
        'java.',
        'javax.',
    ], 'java')
    self.assert_match_classname_prefix([
        'java.rmi.',
        'java.math.',
        'java.beans.',
        'java.security.',
        'java.util.',
        'java.io.',
        'java.lang.',
        'java.nio.',
        'java.awt.',
        'java.sql.',
        'java.text.',
        'java.time.',
        'java.net.',
    ], 'java.')
    self.assert_match_classname_prefix(
        [
            'java.math.BigInteger',
            'java.math.BigInteger.',  # test package
        ],
        'java.math.BigInteger')
    self.assert_match_classname_prefix([
        'java.math.BigInteger.PrimitiveConversionTests',
        'java.math.BigInteger.CompareToTests',
        'java.math.BigInteger.UnicodeConstructor',
        'java.math.BigInteger.BigIntegerTest',
        'java.math.BigInteger.TestValueExact',
        'java.math.BigInteger.StringConstructorOverflow',
        'java.math.BigInteger.ExtremeShiftingTests',
        'java.math.BigInteger.DivisionOverflow',
        'java.math.BigInteger.OperatorNpeTests',
        'java.math.BigInteger.ModPow65537',
        'java.math.BigInteger.LargeValueExceptions',
        'java.math.BigInteger.ProbablePrime',
        'java.math.BigInteger.BitLengthOverflow',
        'java.math.BigInteger.StringConstructor',
        'java.math.BigInteger.PrimeTest',
        'java.math.BigInteger.ModPow',
        'java.math.BigInteger.ModInvTime',
        'java.math.BigInteger.DoubleValueOverflow',
        'java.math.BigInteger.SymmetricRangeTests',
        'java.math.BigInteger.ModPowPowersof2',
    ], 'java.math.BigInteger.')

  def assert_match_classname_prefix(self, expected: List[str], prefix: str):
    result = self.finder.match_classname_prefix(prefix)
    self.assertEqual(set(expected), set(result))


if __name__ == '__main__':
  unittest.main()
