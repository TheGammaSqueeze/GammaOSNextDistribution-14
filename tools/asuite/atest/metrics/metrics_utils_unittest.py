#!/usr/bin/env python3
#
# Copyright 2022, The Android Open Source Project
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

"""Unittests for metrics_utils."""

# pylint: disable=invalid-name

from io import StringIO
import sys
import unittest
from unittest import mock

from atest.metrics import metrics_utils


class MetricsUtilsUnittests(unittest.TestCase):
  """Unit tests for metrics_utils.py"""

  def setUp(self) -> None:
    self.maxDiff = None

  @mock.patch('atest.metrics.metrics_base.get_user_type')
  def test_print_data_collection_notice(self, mock_get_user_type):
    """Test method print_data_collection_notice."""

    # get_user_type return 1(external).
    mock_get_user_type.return_value = 1
    notice_str = (
        '\n==================\nNotice:\n   We collect anonymous usage'
        ' statistics in accordance with our Content Licenses'
        ' (https://source.android.com/setup/start/licenses), Contributor'
        ' License Agreement (https://opensource.google.com/docs/cla/), Privacy'
        ' Policy (https://policies.google.com/privacy) and Terms of Service'
        ' (https://policies.google.com/terms).\n==================\n\n'
    )
    capture_output = StringIO()
    sys.stdout = capture_output
    metrics_utils.print_data_collection_notice(colorful=False)
    sys.stdout = sys.__stdout__
    self.assertEqual(capture_output.getvalue(), notice_str)

    # get_user_type return 0(internal).
    red = '31m'
    green = '32m'
    start = '\033[1;'
    end = '\033[0m'
    mock_get_user_type.return_value = 0
    notice_str = (
        f'\n==================\n{start}{red}Notice:{end}\n'
        f'{start}{green}   We collect usage statistics '
        'in accordance with our '
        'Content Licenses (https://source.android.com/setup/start/licenses), '
        'Contributor License Agreement (https://cla.developers.google.com/), '
        'Privacy Policy (https://policies.google.com/privacy) and '
        f'Terms of Service (https://policies.google.com/terms).{end}'
        '\n==================\n\n'
    )
    capture_output = StringIO()
    sys.stdout = capture_output
    metrics_utils.print_data_collection_notice()
    sys.stdout = sys.__stdout__
    self.assertEqual(capture_output.getvalue(), notice_str)
