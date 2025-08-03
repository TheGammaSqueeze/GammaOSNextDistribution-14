#!/usr/bin/env python3
#
# Copyright 2019, The Android Open Source Project
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

"""Unittest for indexing.py"""

import os
import pickle
import subprocess
import tempfile
import unittest
from unittest import mock

from atest import atest_utils as au
from atest import unittest_constants as uc
from atest.tools import indexing

PRUNEPATH = uc.TEST_CONFIG_DATA_DIR
LOCATE = indexing.LOCATE
UPDATEDB = indexing.UPDATEDB
with tempfile.TemporaryDirectory() as temp_dir:
  ENVIRONMENT = {
      'ANDROID_BUILD_TOP': uc.TEST_DATA_DIR,
      'ANDROID_HOST_OUT': temp_dir,
  }
PRUNEPATHS = [os.path.basename(uc.TEST_CONFIG_DATA_DIR)]


# pylint: disable=protected-access
class IndexTargetUnittests(unittest.TestCase):
  """ "Unittest Class for indexing.py."""

  # TODO: (b/265245404) Re-write test cases with AAA style.
  # TODO: (b/242520851) constants.LOCATE_CACHE should be in literal.
  def test_index_targets(self):
    """Test method index_targets."""
    if au.has_command(UPDATEDB) and au.has_command(LOCATE):
      with mock.patch.dict('os.environ', ENVIRONMENT, clear=True):
        indices = indexing.Indices()

        # 0. Test run_updatedb() is functional.
        indexing.run_updatedb(indices.locate_db, prunepaths=PRUNEPATHS)
        self.assertTrue(indices.locate_db.exists())

        indexing._index_targets(indices, 0.0)
        # 1. test_config/ is excluded so that a.xml won't be found.
        locate_cmd1 = [LOCATE, '-d', indices.locate_db, '/a.xml']
        # locate returns non-zero when target not found; therefore, use run
        # method and assert stdout only.
        result = subprocess.run(locate_cmd1, check=False, capture_output=True)
        self.assertEqual(result.stdout.decode(), '')

        # module-info.json can be found in the search_root.
        locate_cmd2 = [LOCATE, '-d', indices.locate_db, 'module-info.json']
        self.assertEqual(subprocess.call(locate_cmd2), 0)

        # 2. Test get_java_result is functional.
        # 2.1 Test finding a Java class.
        with open(indices.classes_idx, 'rb') as cache:
          _cache = pickle.load(cache)
        self.assertIsNotNone(_cache.get('PathTesting'))
        # 2.2 Test finding a package.
        with open(indices.packages_idx, 'rb') as cache:
          _cache = pickle.load(cache)
        self.assertIsNotNone(_cache.get(uc.PACKAGE))
        # 2.3 Test finding a fully qualified class name.
        with open(indices.fqcn_idx, 'rb') as cache:
          _cache = pickle.load(cache)
        self.assertIsNotNone(_cache.get('android.jank.cts.ui.PathTesting'))

        # 3. Test get_cc_result is functional.
        # 3.1 Test finding a CC class.
        with open(indices.cc_classes_idx, 'rb') as cache:
          _cache = pickle.load(cache)
        self.assertIsNotNone(_cache.get('HelloWorldTest'))
    else:
      self.assertEqual(au.has_command(UPDATEDB), False)
      self.assertEqual(au.has_command(LOCATE), False)


if __name__ == '__main__':
  unittest.main()
