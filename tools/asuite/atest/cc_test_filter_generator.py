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

"""Tool used to compute the test filter for cc tests.

This tool reuses the ATest's logic to compute the test filter.

Usage:
    ./cc_test_filter_generator.py --out <path to output file> \
      --class-file <path to cc file> \
      --class-file <path to cc file> \
      --class-method-reference ClassA#method1,method2 \
      --class-method-reference ClassB#method3 \
      --class-method-reference ClassC
"""

import argparse
from collections import deque
import enum
import os

from tools.asuite.atest import constants_default
from tools.asuite.atest.test_finders import test_filter_utils


@enum.unique
class CCCommentType(enum.Enum):
  BLOCK_COMMENT = '/*'
  LINE_COMMENT = '//'
  NO_COMMENT = 'no comment'


def trim_comments(content):
  """Replace comments with single spaces.

  Each character in the comment should be replaced by a space.

  There are two kinds of comments:
      1. Block comments begin with /* and continue until the next */. Block
      comments do not nest:
          /* this is /* one comment */ text outside comment

      2. Line comments begin with // and continue to the end of the current
      line. Line comments do not nest either, but it does not matter, because
      they would end in the same place anyway.

  It is safe to put line comments inside block comments, or vice versa:

      /* block comment
         // contains line comment
         yet more comment
       */ outside comment

      // line comment /* contains block comment */
  """
  trimed_lines = []
  lines = deque(content.splitlines())

  while lines:
    line = lines.popleft()
    comment_type, index = _get_comment_type(line)

    if comment_type == CCCommentType.NO_COMMENT:
      trimed_lines.append(line.rstrip())
    elif comment_type == CCCommentType.LINE_COMMENT:
      trimed_line = line[0:index] if index > 0 else ''
      trimed_lines.append(trimed_line.rstrip())
      continue
    else:
      code_lines = []
      code_line, comment_ended = _handle_block_comment_line(
          line[index + 2 :] if index + 2 < len(line) else ''
      )

      # Replace each character in the comment by a single space including
      # '/*' and '*/'.
      code_line = f'{line[:index]}  {code_line}'
      code_lines.append(code_line)
      while not comment_ended and lines:
        code_line, comment_ended = _handle_block_comment_line(lines.popleft())
        code_lines.append(code_line)

      # Add the code lines back into unprocessed lines to handle the case
      # like /* x */ code /* x */.
      while code_lines:
        lines.appendleft(code_lines.pop())

  return '\n'.join(trimed_lines).strip('\n')


def _handle_block_comment_line(line):
  if not line:
    return '', False

  # By default, the whole line is comment.
  comment_ended = False
  index_end = len(line) - 1
  if '*/' in line:
    index_end = line.index('*/') + 1
    comment_ended = True

  # Replace each character in the comment by a single space.
  chars = []
  for idx in range(0, len(line)):
    chars.append(line[idx] if idx > index_end else ' ')

  return ''.join(chars), comment_ended


def _get_comment_type(line):
  for idx in range(0, len(line) - 1):
    mark = line[idx : idx + 2]
    if mark == CCCommentType.LINE_COMMENT.value:
      return CCCommentType.LINE_COMMENT, idx
    if mark == CCCommentType.BLOCK_COMMENT.value:
      return CCCommentType.BLOCK_COMMENT, idx

  return CCCommentType.NO_COMMENT, -1


def _parse_class_method_reference(class_method_reference):
  if '#' not in class_method_reference:
    if ',' in class_method_reference:
      raise ValueError(
          'Test methods must follow their class name separated by a `#`, '
          'for example, class#method1,method2'
      )

  methods = []
  if '#' not in class_method_reference:
    class_name = class_method_reference
  else:
    class_name, methods = class_method_reference.split('#', 1)
    methods = methods.split(',')

  return class_name, methods


def _get_test_filters(args):
  class_to_methods = {}
  for class_method_reference in args.class_method_reference:
    class_name, methods = _parse_class_method_reference(class_method_reference)
    if class_name not in class_to_methods:
      class_to_methods[class_name] = set(methods)
    else:
      class_to_methods[class_name] |= set(methods)

  class_info = {}
  for class_file in args.class_file:
    if not constants_default.CC_EXT_RE.match(class_file):
      continue

    if not os.path.isfile(class_file):
      continue

    with open(class_file, 'r') as f:
      info, _ = test_filter_utils.get_cc_class_info(trim_comments(f.read()))

    class_info.update(info)

  test_filters = []
  for cls in class_to_methods:
    if cls not in class_info:
      raise ValueError(f'Class, {cls}, not found in the source files!')

    test_filters.append(
        test_filter_utils.get_cc_filter(class_info, cls, class_to_methods[cls])
    )

  return test_filters


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
      help='Compute the cc test filter to match this class and methods',
  )
  args = parser.parse_args()

  test_filters = []
  if args.class_method_reference and args.class_file:
    test_filters = _get_test_filters(args)

  with open(args.out, 'w') as f:
    f.write(':'.join(test_filters))
