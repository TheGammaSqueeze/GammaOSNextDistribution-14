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

"""Unittest for acloud_create."""

import os
import unittest

from atest import constants
from atest import unittest_constants as uc
from atest.atest_enum import ExitCode
from atest.tools import start_avd

SEARCH_ROOT = uc.TEST_DATA_DIR


class StartAVDUnittests(unittest.TestCase):
  """ "Unittest Class for start_avd.py."""

  def test_get_report_file(self):
    """Test method get_report_file."""
    report_file = '/tmp/acloud_status.json'

    arg_with_equal = '-a --report-file={} --all'.format(report_file)
    self.assertEqual(
        start_avd.get_report_file('/abc', arg_with_equal), report_file
    )

    arg_with_equal = '-b --report_file={} --ball'.format(report_file)
    self.assertEqual(
        start_avd.get_report_file('/abc', arg_with_equal), report_file
    )

    arg_without_equal = '-c --report-file {} --call'.format(report_file)
    self.assertEqual(
        start_avd.get_report_file('/abc', arg_without_equal), report_file
    )

    arg_without_equal = '-d --report_file {} --dall'.format(report_file)
    self.assertEqual(
        start_avd.get_report_file('/abc', arg_without_equal), report_file
    )

    arg_without_report = '-e --build-id 1234567'
    self.assertEqual(
        start_avd.get_report_file('/tmp', arg_without_report), report_file
    )

  def test_probe_acloud_status(self):
    """Test method prob_acloud_status."""
    duration = 100
    success = os.path.join(SEARCH_ROOT, 'acloud', 'create_success.json')
    self.assertEqual(
        start_avd.probe_acloud_status(success, duration), ExitCode.SUCCESS
    )
    self.assertEqual(os.environ[constants.ANDROID_SERIAL], '127.0.0.1:58167')

    success_local_instance = os.path.join(
        SEARCH_ROOT, 'acloud', 'create_success_local_instance.json'
    )
    self.assertEqual(
        start_avd.probe_acloud_status(success_local_instance, duration),
        ExitCode.SUCCESS,
    )
    self.assertEqual(os.environ[constants.ANDROID_SERIAL], '0.0.0.0:6521')

    failure = os.path.join(SEARCH_ROOT, 'acloud', 'create_failure.json')
    self.assertEqual(
        start_avd.probe_acloud_status(failure, duration),
        ExitCode.AVD_CREATE_FAILURE,
    )

    inexistence = os.path.join(SEARCH_ROOT, 'acloud', 'inexistence.json')
    self.assertEqual(
        start_avd.probe_acloud_status(inexistence, duration),
        ExitCode.AVD_INVALID_ARGS,
    )

  def test_get_acloud_duration(self):
    """Test method get_acloud_duration."""
    success = os.path.join(SEARCH_ROOT, 'acloud', 'create_success.json')
    success_duration = 152.659824
    self.assertEqual(start_avd.get_acloud_duration(success), success_duration)

    failure = os.path.join(SEARCH_ROOT, 'acloud', 'create_failure.json')
    failure_duration = 178.621254
    self.assertEqual(start_avd.get_acloud_duration(failure), failure_duration)


if __name__ == '__main__':
  unittest.main()
