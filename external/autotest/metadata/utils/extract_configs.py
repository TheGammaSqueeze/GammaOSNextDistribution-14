# Lint as: python3
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import ast
import collections
import glob
import logging
import os
import shutil
from typing import Generator, List, DefaultDict, NoReturn, Optional, Tuple

import control_files

_ROOT_DIR = os.path.realpath(
    os.path.join(os.path.realpath(__file__), '../../..'))
_METADATA_DIR = os.path.join(_ROOT_DIR, 'metadata')
_CATEGORY_DEFS_DIR = os.path.join(_METADATA_DIR, 'tests')
_CATEGORY_DEFS_PACKAGE = '//metadata/tests'
_ALL_TESTS_FILE = os.path.join(_METADATA_DIR, 'tests.star')


class ExtractionError(Exception):
  """Generic error from this package."""


def main():
  """Extract starlark metadata for all tests in this project.

  This script generates the starlark config files by parsing control files.
  The intent is to backfill majority of the required starlark configs this way,
  followed by hand-editing.
  """
  logging.basicConfig(level=logging.INFO)
  controls = control_files.load_all()
  logging.info('Loaded %d control files', len(controls))
  categorized = dict(_categorize_control_files(controls))
  logging.info('Categorized control files into %d categories', len(categorized))

  _delete_existing_defs()
  for category, control in categorized.items():
    with open(_category_def_file(category), 'w') as f:
      f.write(_generate_category_def(control))
  logging.info('Wrote starlark files to %s', _CATEGORY_DEFS_DIR)
  with open(_ALL_TESTS_FILE, 'w') as f:
    f.write(_generate_all_tests_def(categorized.keys()))
  logging.info('Included all categories in %s', _ALL_TESTS_FILE)


def _delete_existing_defs() -> NoReturn:
  if os.path.exists(_CATEGORY_DEFS_DIR):
    shutil.rmtree(_CATEGORY_DEFS_DIR)
    os.mkdir(_CATEGORY_DEFS_DIR)
  if os.path.exists(_ALL_TESTS_FILE):
    os.unlink(_ALL_TESTS_FILE)


def _categorize_control_files(controls: List[control_files.Control]
                             ) -> DefaultDict[str, control_files.Control]:
  categorized = collections.defaultdict(list)
  for c in controls:
    categorized[c.category].append(c)
  if 'uncategorized' in categorized:
    raise ExtractionError('"uncategorized" is reserved')
  if '' in categorized:
    categorized['uncategorized'] = categorized['']
    del categorized['']
  return categorized


def _category_def_file(category: str) -> str:
  return os.path.join(_CATEGORY_DEFS_DIR, '%s.star' % category)


_CATEGORY_TEMPLATE = """
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# !!! GENERATED FILE. DO NOT EDIT !!!

load('//metadata/test_common.star', 'test_common')

def define_tests():
    return [%(test_list)s
    ]
"""

_TEST_TEMPLATE = """
        test_common.define_test(
            %(name)s,
            suites = %(suites)s,
            main_package = %(main_package)s,
        )"""


def _generate_category_def(controls: List[control_files.Control]) -> str:
  controls = sorted(controls, key=lambda c: c.name)
  test_list = ','.join([
      _TEST_TEMPLATE % {
          'name': "'%s/%s'" % (c.category, c.name),
          'suites': sorted(c.suites),
          'main_package': "'%s'" % c.main_package,
      } for c in controls
  ])
  return _CATEGORY_TEMPLATE % {'test_list': test_list}


_ALL_CATEGORIES_TEMPLATE = """
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# !!! GENERATED FILE. DO NOT EDIT !!!

%(load_inclusions)s

def define_tests():
    tests = []
    %(append_inclusions)s
    return tests
"""

_LOAD_CATEGORY_TEMPLATE = """
load('%(path)s', define_%(name)s = 'define_tests')"""

_APPEND_CATEGORY_TEMPLATE = """
    tests += define_%(name)s()"""


def _generate_all_tests_def(categories: List[str]) -> str:
  load_inclusions = [
      _LOAD_CATEGORY_TEMPLATE % {
          'name': c,
          'path': '%s/%s.star' % (_CATEGORY_DEFS_PACKAGE, c),
      } for c in categories
  ]
  append_inclusions = [
      _APPEND_CATEGORY_TEMPLATE % {
          'name': c
      } for c in categories
  ]
  return _ALL_CATEGORIES_TEMPLATE % {
      'load_inclusions': ''.join(load_inclusions),
      'append_inclusions': ''.join(append_inclusions),
  }


if __name__ == '__main__':
  main()
