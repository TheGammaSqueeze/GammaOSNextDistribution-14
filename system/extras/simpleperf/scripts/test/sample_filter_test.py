#!/usr/bin/env python3
#
# Copyright (C) 2024 The Android Open Source Project
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

import json
import os
from pathlib import Path
import re
import tempfile
from typing import List, Optional, Set

from . test_utils import TestBase, TestHelper


class TestSampleFilter(TestBase):
    def test_show_time_range(self):
        testdata_file = TestHelper.testdata_path('perf_display_bitmaps.data')
        output = self.run_cmd(['sample_filter.py', '-i', testdata_file,
                              '--show-time-range'], return_output=True)
        self.assertIn('0.134 s', output)

    def test_split_time_range(self):
        testdata_file = TestHelper.testdata_path('perf_display_bitmaps.data')
        self.run_cmd(['sample_filter.py', '-i', testdata_file, '--split-time-range', '2'])
        part1_data = Path('sample_filter_part1').read_text()
        self.assertIn('GLOBAL_BEGIN 684943449406175', part1_data)
        self.assertIn('GLOBAL_END 684943516618526', part1_data)
        part2_data = Path('sample_filter_part2').read_text()
        self.assertIn('GLOBAL_BEGIN 684943516618526', part2_data)
        self.assertIn('GLOBAL_END 684943583830876', part2_data)
