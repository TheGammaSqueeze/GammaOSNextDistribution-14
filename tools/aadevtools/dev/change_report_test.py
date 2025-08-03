#!/usr/bin/python3
#
# Copyright (C) 2021 The Android Open Source Project
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
"""Tests for change_report.py."""

import os
import pathlib
import unittest

import change_report


class ChangeReportTest(unittest.TestCase):
  """Tests for ChangeReport."""

  def setUp(self):
    super().setUp()
    old_dir = str(ChangeReportTest.get_resrouce_path('old_codebase'))
    new_dir = str(ChangeReportTest.get_resrouce_path('new_codebase'))
    self.change_report = change_report.ChangeReport(
        old_dir, new_dir, state_filter='0,1,2,3,4')

  def test_get_diff_stat_lines(self):
    """Tests if the diff stat of new & old_codebase matches change_report-new_vs_old_codebase.csv."""
    diff_stat_lines = []
    diff_stat_lines.append(change_report.ChangeReport.get_diff_stat_header())
    diff_stat_lines.extend(self.change_report.get_diff_stat_lines())

    expected_diff_stat_lines = ChangeReportTest.get_expected_lines(
        'change_report-new_vs_old_codebase.csv')

    offending_line_indexes = ChangeReportTest.diff_lines(
        expected_diff_stat_lines, diff_stat_lines)
    self.assertEqual(len(offending_line_indexes), 0)

  def test_get_diff_lines(self):
    """Tests if the diff stat of new & old_codebase matches change_report_diff-new_vs_old_codebase.txt."""
    diff_lines = self.change_report.get_diff_lines()

    expected_diff_lines = ChangeReportTest.get_expected_lines(
        'change_report_diff-new_vs_old_codebase.txt')

    offending_line_indexes = ChangeReportTest.diff_lines(
        expected_diff_lines, diff_lines)
    self.assertEqual(len(offending_line_indexes), 0)

  @staticmethod
  def get_resrouce_path(target):
    # .../dev/change_report_test.py
    this_path = pathlib.Path(os.path.abspath(__file__)).parents[0]
    return pathlib.Path(this_path, 'resource', target)

  @staticmethod
  def get_expected_lines(target):
    file = ChangeReportTest.get_resrouce_path(target)
    with open(file, 'r') as f:
      lines = f.readlines()
    return lines

  @staticmethod
  def diff_lines(expected, actual):
    expected_len = len(expected)
    actual_len = len(actual)
    offending_line_indexes = []

    if actual_len < expected_len:
      l = actual_len
    else:
      l = expected_len

    for i in range(l):
      if expected[i] != actual[i]:
        print('ERROR: line %d is not as expected' % i)
        print(expected[i])
        print(actual[i])
        offending_line_indexes.append(i)

    if actual_len < expected_len:
      print('ERROR: Missing %d lines' % (expected_len - actual_len))
      for j in range(actual_len, expected_len):
        print(expected[j])
        offending_line_indexes.append(j)
    elif actual_len > expected_len:
      print('ERROR: Extra %d lines' % (actual_len - expected_len))
      for k in range(expected_len, actual_len):
        print(actual[k])
        offending_line_indexes.append(k)
    return offending_line_indexes


if __name__ == '__main__':
  unittest.main()
