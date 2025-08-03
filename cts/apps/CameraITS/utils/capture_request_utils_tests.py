# Copyright 2023 The Android Open Source Project
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
"""Tests for capture_request_utils."""


import math
import unittest

import capture_request_utils


class CaptureRequestUtilsTest(unittest.TestCase):
  """Unit tests for this module.

  Ensures rational number conversion dicts are created properly.
  """
  _FLOAT_HALF = 0.5
  # No immutable container: frozendict requires package install on partner host
  _RATIONAL_HALF = {'numerator': 32, 'denominator': 64}

  def test_float_to_rational(self):
    """Unit test for float_to_rational."""
    self.assertEqual(
        capture_request_utils.float_to_rational(self._FLOAT_HALF, 64),
        self._RATIONAL_HALF)

  def test_rational_to_float(self):
    """Unit test for rational_to_float."""
    self.assertTrue(
        math.isclose(capture_request_utils.rational_to_float(
            self._RATIONAL_HALF), self._FLOAT_HALF, abs_tol=0.0001))

  def test_int_to_rational(self):
    """Unit test for int_to_rational."""
    rational_10 = {'numerator': 10, 'denominator': 1}
    rational_1 = {'numerator': 1, 'denominator': 1}
    rational_2 = {'numerator': 2, 'denominator': 1}
    # Simple test
    self.assertEqual(capture_request_utils.int_to_rational(10), rational_10)
    # Handle list entries
    self.assertEqual(
        capture_request_utils.int_to_rational([1, 2]),
        [rational_1, rational_2])

if __name__ == '__main__':
  unittest.main()
