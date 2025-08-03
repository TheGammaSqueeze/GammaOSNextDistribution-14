#!/usr/bin/env python3
#
# Copyright 2018 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Main entrypoint for all of atest's unittest."""

from importlib import import_module
import logging
import os
import sys
import unittest
from unittest import mock

from atest import unittest_constants

# Setup logging to be silent so unittests can pass through TF.
logging.disable(logging.ERROR)

# TODO: (b/275449248) remove these mock variables and create these mock on
# demand in class-wide.
ENV = {
    'ANDROID_BUILD_TOP': '/',
    'ANDROID_PRODUCT_OUT': '/out/prod',
    'ANDROID_TARGET_OUT_TESTCASES': '/out/prod/tcases',
    'ANDROID_HOST_OUT': '/out/host',
    'ANDROID_HOST_OUT_TESTCASES': '/out/host/tcases',
    'TARGET_PRODUCT': 'aosp_cf_x86_64',
    'TARGET_BUILD_VARIANT': 'userdebug',
}


def get_test_modules():
  """Returns a list of testable modules.

  Finds all the test files (*_unittest.py) and get their no-absolute
  path (internal/lib/utils_test.py) and translate it to an import path and
  strip the py ext (internal.lib.utils_test).

  Returns:
      List of strings (the testable module import path).
  """
  testable_modules = []
  package = unittest_constants.ATEST_PKG_DIR
  base_path = os.path.dirname(package)

  for dirpath, _, files in os.walk(package):
    for f in files:
      if f.endswith('_unittest.py') or f.endswith('_unittest.pyc'):
        # Now transform it into a no-absolute import path.
        full_file_path = os.path.join(dirpath, f)
        rel_file_path = os.path.relpath(full_file_path, base_path)
        rel_file_path, _ = os.path.splitext(rel_file_path)
        rel_file_path = rel_file_path.replace(os.sep, '.')
        testable_modules.append(rel_file_path)

  return testable_modules


def run_test_modules(test_modules):
  """Main method of running unit tests.

  Args: test_modules; a list of module names.

  Returns:
      result: a namespace of unittest result.
  """
  for mod in test_modules:
    import_module(mod)

  loader = unittest.defaultTestLoader
  test_suite = loader.loadTestsFromNames(test_modules)
  runner = unittest.TextTestRunner(verbosity=2)
  return runner.run(test_suite)


if __name__ == '__main__':
  print(sys.version_info)
  with mock.patch.dict('os.environ', ENV):
    result = run_test_modules(get_test_modules())
    if not result.wasSuccessful():
      sys.exit(not result.wasSuccessful())
    sys.exit(0)
