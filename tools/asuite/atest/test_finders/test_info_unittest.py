#!/usr/bin/env python3
#
# Copyright 2020, The Android Open Source Project
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

"""Unittests for cache_finder."""


import unittest

from atest.test_finders import test_info


# pylint: disable=protected-access
class TestInfoUnittests(unittest.TestCase):
  """Unit tests for cache_finder.py"""

  def test_get_test_path(self):
    """Test test_get_test_paths method."""
    build_targets = set()
    exp_rel_paths = ['a-b-c', 'd-e-f']
    for exp_rel_path in exp_rel_paths:
      build_targets.add('MODULES-IN-%s' % exp_rel_path.replace('/', '-'))
    t_info = test_info.TestInfo('mock_name', 'mock_runner', build_targets)
    self.assertEqual(sorted(t_info.get_test_paths()), sorted(exp_rel_paths))


if __name__ == '__main__':
  unittest.main()
