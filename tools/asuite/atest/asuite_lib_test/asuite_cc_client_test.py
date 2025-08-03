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


"""Unittest for atest_execution_info."""

import unittest


class AsuiteCCLibTest(unittest.TestCase):
  """Tests for verify asuite_metrics libs"""

  def test_import_asuite_cc_lib(self):
    """Test asuite_cc_lib."""
    # pylint: disable=unused-variable
    # pylint: disable=import-outside-toplevel
    # pylint: disable=unused-import
    from atest.metrics import metrics
    from atest.metrics import metrics_base
    from atest.metrics import metrics_utils

    # TODO (b/132602907): Add the real usage for checking if metrics pass or
    #  fail.
    metrics_base.MetricsBase.tool_name = 'MyTestTool'
    metrics_utils.get_start_time()
    metrics.AtestStartEvent(
        command_line='test_command',
        test_references='test_reference',
        cwd='test_cwd',
        os='test_os',
    )


if __name__ == '__main__':
  unittest.main()
