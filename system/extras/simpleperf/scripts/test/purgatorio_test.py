#!/usr/bin/env python3
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

import collections
import json
import os
from typing import Any, Dict, List

from . test_utils import TestBase, TestHelper


class TestPurgatorio(TestBase):
    def setUp(self):
        super().setUp()
        self.script_path = os.path.join('purgatorio', 'purgatorio.py')

    def get_report(self, options: List[str]) -> str:
        self.run_cmd([self.script_path, '-d', '-o', 'report.html'] + options)
        with open('report.html', 'r') as fh:
            return fh.read()

    def test_proguard_mapping_file(self):
        """ Test --proguard-mapping-file option. """
        testdata_file = TestHelper.testdata_path('perf_need_proguard_mapping.data')
        proguard_mapping_file = TestHelper.testdata_path('proguard_mapping.txt')
        original_methodname = 'androidx.fragment.app.FragmentActivity.startActivityForResult'
        # Can't show original method name without proguard mapping file.
        self.assertNotIn(original_methodname, self.get_report(['-i', testdata_file]))
        # Show original method name with proguard mapping file.
        self.assertIn(original_methodname, self.get_report(
            ['--proguard-mapping-file', proguard_mapping_file, '-i', testdata_file]))

    def test_show_art_frames(self):
        art_frame_str = 'art::interpreter::DoCall'
        options = ['-i', TestHelper.testdata_path('perf_with_interpreter_frames.data')]
        report = self.get_report(options)
        self.assertIn(art_frame_str, report)
        report = self.get_report(options + ['--no-show-art-frames'])
        self.assertNotIn(art_frame_str, report)
