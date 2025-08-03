#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# Copyright 2020 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Tests for remove_cold_functions."""


import io
import unittest
from unittest.mock import patch

from afdo_redaction import remove_cold_functions


def _construct_profile(indices=None):
    real_world_profile_functions = [
        """SomeFunction1:24150:300
 2: 75
 3: 23850
 39: 225
      """,
        """SomeFunction2:8925:225
 0: 225
 0.2: 150
 0.1: SomeFunction2:6300
  3: 6300
 0.2: SomeFunction2:150
  3: 75
      """,
        """SomeFunction3:7500:75
 0: 75
 0.2: 75
 0.1: SomeFunction3:6600
  1: 6600
 0.2: SomeFunction3:75
  1: 75
      """,
        """LargerFunction4:51450:0
 1: 0
 3: 0
 3.1: 7350
 4: 7350
 7: 7350
 8: 7350
 9: 7350
 12: 0
 15: 0
 13: AnotherFunction5:0
  3: 0
  3.1: 0
  3.2: 0
  4: 0
  5: 0
  6: 0
  7: 0
  8: 0
  9: 0
      """,
        """SomeFakeFunction5:7500:75
 0: 75
 0.2: 75
 0.1: SomeFakeFunction5:6600
  1: 6600
 0.2: SomeFakeFunction5:75
  1: 75
      """,
    ]

    ret = []
    if not indices:
        for x in real_world_profile_functions:
            ret += x.strip().splitlines()
        return ret

    ret = []
    for i in indices:
        ret += real_world_profile_functions[i].strip().splitlines()
    return ret


def _run_test(input_lines, goal, cwp_file=None, benchmark_file=None):
    input_buf = io.StringIO("\n".join(input_lines))
    output_buf = io.StringIO()
    remove_cold_functions.run(
        input_buf, output_buf, goal, cwp_file, benchmark_file
    )
    return output_buf.getvalue().splitlines()


class Test(unittest.TestCase):
    """Test functions in remove_cold_functions.py"""

    def test_empty_profile(self):
        self.assertEqual(_run_test([], 0), [])

    def test_remove_all_functions_fail(self):
        input_profile_lines = _construct_profile()
        with self.assertRaises(Exception) as context:
            _run_test(input_profile_lines, 0)
        self.assertEqual(
            str(context.exception),
            "It's invalid to remove all functions in the profile",
        )

    def test_remove_cold_functions_work(self):
        input_profile_lines = _construct_profile()
        # To make sure the cold functions are removed in order
        expected_profile_lines = {
            5: input_profile_lines,
            # Entry 4 wins the tie breaker because the name is smaller
            # alphabetically.
            4: _construct_profile([0, 1, 3, 4]),
            3: _construct_profile([0, 1, 3]),
            2: _construct_profile([0, 3]),
            1: _construct_profile([3]),
        }

        for num in expected_profile_lines:
            self.assertCountEqual(
                _run_test(input_profile_lines, num), expected_profile_lines[num]
            )

    def test_analyze_cwp_and_benchmark_work(self):
        input_profile_lines = _construct_profile()
        cwp_profile = _construct_profile([0, 1, 3, 4])
        benchmark_profile = _construct_profile([1, 2, 3, 4])
        cwp_buf = io.StringIO("\n".join(cwp_profile))
        benchmark_buf = io.StringIO("\n".join(benchmark_profile))
        with patch("sys.stderr", new=io.StringIO()) as fake_output:
            _run_test(input_profile_lines, 3, cwp_buf, benchmark_buf)

        output = fake_output.getvalue()
        self.assertIn("Retained 3/5 (60.0%) functions in the profile", output)
        self.assertIn(
            "Retained 1/1 (100.0%) functions only appear in the CWP profile",
            output,
        )
        self.assertIn(
            "Retained 0/1 (0.0%) functions only appear in the benchmark profile",
            output,
        )
        self.assertIn(
            "Retained 2/3 (66.7%) functions appear in both CWP and benchmark"
            " profiles",
            output,
        )


if __name__ == "__main__":
    unittest.main()
