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

import logging
import os
import re


# Gtest Types
GTEST_REGULAR = 'regular native test'
GTEST_TYPED = 'typed test'
GTEST_TYPED_PARAM = 'typed-parameterized test'
GTEST_PARAM = 'value-parameterized test'


# Macros that used in GTest. Detailed explanation can be found in
# $ANDROID_BUILD_TOP/external/googletest/googletest/samples/sample*_unittest.cc
# 1. Traditional Tests:
#   TEST(class, method)
#   TEST_F(class, method)
# 2. Type Tests:
#   TYPED_TEST_SUITE(class, types)
#     TYPED_TEST(class, method)
# 3. Value-parameterized Tests:
#   TEST_P(class, method)
#     INSTANTIATE_TEST_SUITE_P(Prefix, class, param_generator, name_generator)
# 4. Type-parameterized Tests:
#   TYPED_TEST_SUITE_P(class)
#     TYPED_TEST_P(class, method)
#       REGISTER_TYPED_TEST_SUITE_P(class, method)
#         INSTANTIATE_TYPED_TEST_SUITE_P(Prefix, class, Types)
# Macros with (class, method) pattern.
CC_CLASS_METHOD_RE = re.compile(
    r'^\s*(TYPED_TEST(?:|_P)|TEST(?:|_F|_P))\s*\(\s*'
    r'(?P<class_name>\w+),\s*(?P<method_name>\w+)\)\s*\{',
    re.M,
)
# Macros that used in GTest with flags. Detailed example can be found in
# $ANDROID_BUILD_TOP/cts/flags/cc_tests/src/FlagMacrosTests.cpp
# Macros with (prefix, class, ...) pattern.
CC_FLAG_CLASS_METHOD_RE = re.compile(
    r'^\s*(TEST(?:|_F))_WITH_FLAGS\s*\(\s*'
    r'(?P<class_name>\w+),\s*(?P<method_name>\w+),',
    re.M,
)
# Macros with (prefix, class, ...) pattern.
# Note: Since v1.08, the INSTANTIATE_TEST_CASE_P was replaced with
#   INSTANTIATE_TEST_SUITE_P. However, Atest does not intend to change the
#   behavior of a test, so we still search *_CASE_* macros.
CC_PARAM_CLASS_RE = re.compile(
    r'^\s*INSTANTIATE_(?:|TYPED_)TEST_(?:SUITE|CASE)_P\s*\(\s*'
    r'(?P<instantiate>\w+),\s*(?P<class>\w+)\s*,',
    re.M,
)
# Type/Type-parameterized Test macros:
TYPE_CC_CLASS_RE = re.compile(
    r'^\s*TYPED_TEST_SUITE(?:|_P)\(\s*(?P<class_name>\w+)', re.M
)

# RE for suspected parameterized java/kt class.
_SUSPECTED_PARAM_CLASS_RE = re.compile(
    r'^\s*@RunWith\s*\(\s*(TestParameterInjector|'
    r'JUnitParamsRunner|DataProviderRunner|JukitoRunner|Theories|BedsteadJUnit4'
    r')(\.|::)class\s*\)',
    re.I,
)
# Parse package name from the package declaration line of a java or
# a kotlin file.
# Group matches "foo.bar" of line "package foo.bar;" or "package foo.bar"
_PACKAGE_RE = re.compile(r'\s*package\s+(?P<package>[^(;|\s)]+)\s*', re.I)


class TooManyMethodsError(Exception):
  """Raised when input string contains more than one # character."""


class MoreThanOneClassError(Exception):
  """Raised when multiple classes given in 'classA,classB' pattern."""


class MissingPackageNameError(Exception):
  """Raised when the test class java file does not contain a package name."""


def get_cc_class_info(class_file_content):
  """Get the class info of the given cc class file content.

  The class info dict will be like:
      {'classA': {
          'methods': {'m1', 'm2'}, 'prefixes': {'pfx1'}, 'typed': True},
       'classB': {
          'methods': {'m3', 'm4'}, 'prefixes': set(), 'typed': False},
       'classC': {
          'methods': {'m5', 'm6'}, 'prefixes': set(), 'typed': True},
       'classD': {
          'methods': {'m7', 'm8'}, 'prefixes': {'pfx3'}, 'typed': False}}
  According to the class info, we can tell that:
      classA is a typed-parameterized test. (TYPED_TEST_SUITE_P)
      classB is a regular gtest.            (TEST_F|TEST)
      classC is a typed test.               (TYPED_TEST_SUITE)
      classD is a value-parameterized test. (TEST_P)

  Args:
      class_file_content: Content of the cc class file.

  Returns:
      A tuple of a dict of class info and a list of classes that have no test.
  """
  flag_method_matches = re.findall(CC_FLAG_CLASS_METHOD_RE, class_file_content)
  # ('TYPED_TEST', 'PrimeTableTest', 'ReturnsTrueForPrimes')
  method_matches = re.findall(CC_CLASS_METHOD_RE, class_file_content)
  # ('OnTheFlyAndPreCalculated', 'PrimeTableTest2')
  prefix_matches = re.findall(CC_PARAM_CLASS_RE, class_file_content)
  # 'PrimeTableTest'
  typed_matches = re.findall(TYPE_CC_CLASS_RE, class_file_content)

  classes = {cls[1] for cls in method_matches + flag_method_matches}
  class_info = {}
  for cls in classes:
    class_info.setdefault(
        cls, {'methods': set(), 'prefixes': set(), 'typed': False}
    )

  no_test_classes = []

  logging.debug('Probing TestCase.TestName pattern:')
  for match in method_matches + flag_method_matches:
    if class_info.get(match[1]):
      logging.debug('  Found %s.%s', match[1], match[2])
      class_info[match[1]]['methods'].add(match[2])
    else:
      no_test_classes.append(match[1])

  # Parameterized test.
  logging.debug('Probing InstantiationName/TestCase pattern:')
  for match in prefix_matches:
    if class_info.get(match[1]):
      logging.debug('  Found %s/%s', match[0], match[1])
      class_info[match[1]]['prefixes'].add(match[0])
    else:
      no_test_classes.append(match[1])

  # Typed test
  logging.debug('Probing typed test names:')
  for match in typed_matches:
    if class_info.get(match):
      logging.debug('  Found %s', match)
      class_info[match]['typed'] = True
    else:
      no_test_classes.append(match[1])

  return class_info, no_test_classes


def get_cc_class_type(class_info, classname):
  """Tell the type of the given class.

  Args:
      class_info: A dict of class info.
      classname: A string of class name.

  Returns:
      String of the gtest type to prompt. The output will be one of:
      1. 'regular test'             (GTEST_REGULAR)
      2. 'typed test'               (GTEST_TYPED)
      3. 'value-parameterized test' (GTEST_PARAM)
      4. 'typed-parameterized test' (GTEST_TYPED_PARAM)
  """
  if class_info.get(classname).get('prefixes'):
    if class_info.get(classname).get('typed'):
      return GTEST_TYPED_PARAM
    return GTEST_PARAM
  if class_info.get(classname).get('typed'):
    return GTEST_TYPED
  return GTEST_REGULAR


def get_cc_filter(class_info, class_name, methods):
  """Get the cc filter.

  Args:
      class_info: a dict of class info.
      class_name: class name of the cc test.
      methods: a list of method names.

  Returns:
      A formatted string for cc filter.
      For a Type/Typed-parameterized test, it will be:
        "class1/*.method1:class1/*.method2" or "class1/*.*"
      For a parameterized test, it will be:
        "*/class1.*" or "prefix/class1.*"
      For the rest the pattern will be:
        "class1.method1:class1.method2" or "class1.*"
  """
  # Strip prefix from class_name.
  _class_name = class_name
  if '/' in class_name:
    _class_name = str(class_name).split('/')[-1]
  type_str = get_cc_class_type(class_info, _class_name)
  logging.debug('%s is a "%s".', _class_name, type_str)
  # When found parameterized tests, recompose the class name
  # in */$(ClassName) if the prefix is not given.
  if type_str in (GTEST_TYPED_PARAM, GTEST_PARAM):
    if not '/' in class_name:
      class_name = '*/%s' % class_name
  if type_str in (GTEST_TYPED, GTEST_TYPED_PARAM):
    if methods:
      sorted_methods = sorted(list(methods))
      return ':'.join(['%s/*.%s' % (class_name, x) for x in sorted_methods])
    return '%s/*.*' % class_name
  if methods:
    sorted_methods = sorted(list(methods))
    return ':'.join(['%s.%s' % (class_name, x) for x in sorted_methods])
  return '%s.*' % class_name


def is_parameterized_java_class(test_path):
  """Find out if input test path is a parameterized java class.

  Args:
      test_path: A string of absolute path to the java file.

  Returns:
      Boolean: Is parameterized class or not.
  """
  with open(test_path) as class_file:
    for line in class_file:
      # Return immediately if the @ParameterizedTest annotation is found.
      if re.compile(r'\s*@ParameterizedTest').match(line):
        return True
      # Return when Parameterized.class is invoked in @RunWith annotation.
      # @RunWith(Parameterized.class) -> Java.
      # @RunWith(Parameterized::class) -> kotlin.
      if re.compile(r'^\s*@RunWith\s*\(\s*Parameterized.*(\.|::)class').match(
          line
      ):
        return True
      if _SUSPECTED_PARAM_CLASS_RE.match(line):
        return True
  return False


def get_java_method_filters(class_file, methods):
  """Get a frozenset of method filter when the given is a Java class.

  class_file: The Java/kt file path.
  methods: a set of method string.

  Returns:
      Frozenset of methods.
  """
  method_filters = methods
  if is_parameterized_java_class(class_file):
    update_methods = []
    for method in methods:
      # Only append * to the method if brackets are not a part of
      # the method name, and result in running all parameters of
      # the parameterized test.
      if not _contains_brackets(method, pair=False):
        update_methods.append(method + '*')
      else:
        update_methods.append(method)
    method_filters = frozenset(update_methods)

  return method_filters


def split_methods(user_input):
  """Split user input string into test reference and list of methods.

  Args:
      user_input: A string of the user's input.
                  Examples: class_name class_name#method1,method2 path
                    path#method1,method2

  Returns:
      A tuple. First element is String of test ref and second element is
      a set of method name strings or empty list if no methods included.
  Exception:
      atest_error.TooManyMethodsError raised when input string is trying to
      specify too many methods in a single positional argument.

      Examples of unsupported input strings:
          module:class#method,class#method
          class1#method,class2#method
          path1#method,path2#method
  """
  error_msg = (
      'Too many "{}" characters in user input:\n\t{}\n'
      'Multiple classes should be separated by space, and methods belong to '
      'the same class should be separated by comma. Example syntaxes are:\n'
      '\tclass1 class2#method1 class3#method2,method3\n'
      '\tclass1#method class2#method'
  )
  if not '#' in user_input:
    if ',' in user_input:
      raise MoreThanOneClassError(error_msg.format(',', user_input))
    return user_input, frozenset()
  parts = user_input.split('#')
  if len(parts) > 2:
    raise TooManyMethodsError(error_msg.format('#', user_input))
  # (b/260183137) Support parsing multiple parameters.
  parsed_methods = []
  brackets = ('[', ']')
  for part in parts[1].split(','):
    count = {part.count(p) for p in brackets}
    # If brackets are in pair, the length of count should be 1.
    if len(count) == 1:
      parsed_methods.append(part)
    else:
      # The front part of the pair, e.g. 'method[1'
      if re.compile(r'^[a-zA-Z0-9]+\[').match(part):
        parsed_methods.append(part)
        continue
      # The rear part of the pair, e.g. '5]]', accumulate this part to
      # the last index of parsed_method.
      parsed_methods[-1] += f',{part}'
  return parts[0], frozenset(parsed_methods)


def _contains_brackets(string: str, pair: bool = True) -> bool:
  """Determines whether a given string contains (pairs of) brackets.

  Args:
      string: The string to check for brackets.
      pair: Whether to check for brackets in pairs.

  Returns:
      bool: True if the given contains full pair of brackets; False otherwise.
  """
  if not pair:
    return re.search(r'\(|\)|\[|\]|\{|\}', string)

  stack = []
  brackets = {'(': ')', '[': ']', '{': '}'}
  for char in string:
    if char in brackets:
      stack.append(char)
    elif char in brackets.values():
      if not stack or brackets[stack.pop()] != char:
        return False
  return len(stack) == 0


def get_package_name(file_path):
  """Parse the package name from a java file.

  Args:
      file_path: A string of the absolute path to the java file.

  Returns:
      A string of the package name or None
  """
  with open(file_path) as data:
    for line in data:
      match = _PACKAGE_RE.match(line)
      if match:
        return match.group('package')


# pylint: disable=inconsistent-return-statements
def get_fully_qualified_class_name(test_path):
  """Parse the fully qualified name from the class java file.

  Args:
      test_path: A string of absolute path to the java class file.

  Returns:
      A string of the fully qualified class name.

  Raises:
      atest_error.MissingPackageName if no class name can be found.
  """
  package = get_package_name(test_path)
  if package:
    cls = os.path.splitext(os.path.split(test_path)[1])[0]
    return '%s.%s' % (package, cls)
  raise MissingPackageNameError(
      f'{test_path}: Test class java file does not contain a package name.'
  )
