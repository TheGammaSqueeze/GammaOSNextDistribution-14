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
import re
import tempfile
from typing import Any, Dict, List, Set

from . test_utils import INFERNO_SCRIPT, TestBase, TestHelper


class TestInferno(TestBase):
    def get_report(self, options: List[str]) -> str:
        self.run_cmd([INFERNO_SCRIPT] + options)
        with open('report.html', 'r') as fh:
            return fh.read()

    def test_proguard_mapping_file(self):
        """ Test --proguard-mapping-file option. """
        testdata_file = TestHelper.testdata_path('perf_need_proguard_mapping.data')
        proguard_mapping_file = TestHelper.testdata_path('proguard_mapping.txt')
        original_methodname = 'androidx.fragment.app.FragmentActivity.startActivityForResult'
        # Can't show original method name without proguard mapping file.
        self.assertNotIn(original_methodname, self.get_report(
            ['--record_file', testdata_file, '-sc']))
        # Show original method name with proguard mapping file.
        self.assertIn(original_methodname, self.get_report(
            ['--record_file', testdata_file, '-sc', '--proguard-mapping-file', proguard_mapping_file]))

    def test_trace_offcpu(self):
        """ Test --trace-offcpu option. """
        testdata_file = TestHelper.testdata_path('perf_with_trace_offcpu_v2.data')
        report = self.get_report(['--record_file', testdata_file,
                                  '-sc', '--trace-offcpu', 'off-cpu'])
        self.assertIn('Thread 6525 (com.google.samples.apps.sunflower) (42 samples)', report)

    def test_sample_filters(self):
        def get_threads_for_filter(filter: str) -> Set[int]:
            report = self.get_report(
                ['--record_file', TestHelper.testdata_path('perf_display_bitmaps.data'),
                 '-sc'] + filter.split())
            threads = set()
            pattern = re.compile(r'Thread\s+(\d+)\s+')
            threads = set()
            for m in re.finditer(pattern, report):
                threads.add(int(m.group(1)))
            return threads

        self.assertNotIn(31850, get_threads_for_filter('--exclude-pid 31850'))
        self.assertIn(31850, get_threads_for_filter('--include-pid 31850'))
        self.assertNotIn(31881, get_threads_for_filter('--exclude-tid 31881'))
        self.assertIn(31881, get_threads_for_filter('--include-tid 31881'))
        self.assertNotIn(31881, get_threads_for_filter(
            '--exclude-process-name com.example.android.displayingbitmaps'))
        self.assertIn(31881, get_threads_for_filter(
            '--include-process-name com.example.android.displayingbitmaps'))
        self.assertNotIn(31850, get_threads_for_filter(
            '--exclude-thread-name com.example.android.displayingbitmaps'))
        self.assertIn(31850, get_threads_for_filter(
            '--include-thread-name com.example.android.displayingbitmaps'))

        with tempfile.NamedTemporaryFile('w', delete=False) as filter_file:
            filter_file.write('GLOBAL_BEGIN 684943449406175\nGLOBAL_END 684943449406176')
            filter_file.flush()
            threads = get_threads_for_filter('--filter-file ' + filter_file.name)
            self.assertIn(31881, threads)
            self.assertNotIn(31850, threads)
        os.unlink(filter_file.name)

    def test_show_art_frames(self):
        art_frame_str = 'art::interpreter::DoCall'
        options = ['--record_file',
                   TestHelper.testdata_path('perf_with_interpreter_frames.data'), '-sc']
        report = self.get_report(options)
        self.assertNotIn(art_frame_str, report)
        report = self.get_report(options + ['--show-art-frames'])
        self.assertIn(art_frame_str, report)
