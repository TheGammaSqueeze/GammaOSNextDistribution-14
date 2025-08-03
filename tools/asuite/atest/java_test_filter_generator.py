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

"""Tool used to compute the test filter for Java tests.

This tool reuses the ATest's logic to compute the test filter.

Usage:
    ./java_test_filter_generator --out <path to output file> \
      --class-file <path to java/kt file> \
      --class-file <path to java/kt file> \
      --class-method-reference ClassA#method1,method2 \
      --class-method-reference ClassB#method3 \
      --class-method-reference ClassC
"""

import argparse
from collections import defaultdict
import os

from tools.asuite.atest import constants_default
from tools.asuite.atest.test_finders import test_filter_utils


def _get_test_filters(class_method_references, class_files):
  class_to_methods = defaultdict(set)
  for class_method_reference in class_method_references:
    class_name, methods = test_filter_utils.split_methods(
        class_method_reference
    )
    class_to_methods[class_name] |= set(methods)

  filters = []
  for class_file in class_files:
    if not constants_default.JAVA_EXT_RE.match(class_file):
      continue

    if not os.path.isfile(class_file):
      continue

    full_class_name = test_filter_utils.get_fully_qualified_class_name(
        class_file
    )
    class_name, _ = os.path.splitext(os.path.basename(class_file))
    if class_name not in class_to_methods:
      # Check whether a full class name including packagename is specified.
      if full_class_name not in class_to_methods:
        continue
      class_name = full_class_name

    methods = test_filter_utils.get_java_method_filters(
        class_file, class_to_methods[class_name]
    )
    if methods:
      filters.extend([f'{full_class_name}#{m}' for m in methods])
    else:
      filters.append(full_class_name)

  return filters


if __name__ == '__main__':
  parser = argparse.ArgumentParser()
  parser.add_argument(
      '--out', action='store', required=True, help='Write output to <file>'
  )
  parser.add_argument(
      '--class-file',
      action='append',
      default=[],
      help='Get the class information from the <file>',
  )
  parser.add_argument(
      '--class-method-reference',
      action='append',
      default=[],
      help='Compute the java test filter to match this class and methods',
  )
  args = parser.parse_args()

  test_filters = []
  if args.class_method_reference and args.class_file:
    test_filters = _get_test_filters(
        args.class_method_reference, args.class_file
    )

  with open(args.out, 'w') as f:
    f.write(' '.join(test_filters))
