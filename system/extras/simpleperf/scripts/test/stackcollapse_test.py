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

import json
import os
from pathlib import Path
import re
import tempfile
from typing import List, Optional, Set

from . test_utils import TestBase, TestHelper


class TestStackCollapse(TestBase):
    def get_report(self, testdata_file: str, options: Optional[List[str]] = None) -> str:
        args = ['stackcollapse.py', '-i', TestHelper.testdata_path(testdata_file)]
        if options:
            args.extend(options)
        report = self.run_cmd(args, return_output=True)
        return report.replace('\r', '')

    def test_jit_annotations(self):
        got = self.get_report('perf_with_jit_symbol.data', ['--jit'])
        golden_path = TestHelper.testdata_path('perf_with_jit_symbol.foldedstack')
        self.assertEqual(got, Path(golden_path).read_text())

    def test_kernel_annotations(self):
        got = self.get_report('perf_with_jit_symbol.data', ['--kernel'])
        golden_path = TestHelper.testdata_path('perf_with_jit_symbol.foldedstack_with_kernel')
        self.assertEqual(got, Path(golden_path).read_text())

    def test_with_pid(self):
        got = self.get_report('perf_with_jit_symbol.data', ['--jit', '--pid'])
        golden_path = TestHelper.testdata_path('perf_with_jit_symbol.foldedstack_with_pid')
        self.assertEqual(got, Path(golden_path).read_text())

    def test_with_tid(self):
        got = self.get_report('perf_with_jit_symbol.data', ['--jit', '--tid'])
        golden_path = TestHelper.testdata_path('perf_with_jit_symbol.foldedstack_with_tid')
        self.assertEqual(got, Path(golden_path).read_text())

    def test_two_event_types_chooses_first(self):
        got = self.get_report('perf_with_two_event_types.data')
        golden_path = TestHelper.testdata_path('perf_with_two_event_types.foldedstack')
        self.assertEqual(got, Path(golden_path).read_text())

    def test_two_event_types_chooses_with_event_filter(self):
        got = self.get_report('perf_with_two_event_types.data', ['--event-filter', 'cpu-clock'])
        golden_path = TestHelper.testdata_path('perf_with_two_event_types.foldedstack_cpu_clock')
        self.assertEqual(got, Path(golden_path).read_text())

    def test_unknown_symbol_addrs(self):
        got = self.get_report('perf_with_jit_symbol.data', ['--addrs'])
        golden_path = TestHelper.testdata_path('perf_with_jit_symbol.foldedstack_addrs')
        self.assertEqual(got, Path(golden_path).read_text())

    def test_sample_filters(self):
        def get_threads_for_filter(filter: str) -> Set[int]:
            report = self.get_report('perf_display_bitmaps.data', ['--tid'] + filter.split())
            pattern = re.compile(r'-31850/(\d+);')
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
        report = self.get_report('perf_with_interpreter_frames.data')
        self.assertNotIn(art_frame_str, report)
        report = self.get_report('perf_with_interpreter_frames.data', ['--show-art-frames'])
        self.assertIn(art_frame_str, report)
