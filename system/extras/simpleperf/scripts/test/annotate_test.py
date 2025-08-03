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

import os
from pathlib import Path
import re
import tempfile

from binary_cache_builder import BinaryCacheBuilder
from . test_utils import TestBase, TestHelper


class TestAnnotate(TestBase):
    def test_annotate(self):
        testdata_file = TestHelper.testdata_path('runtest_two_functions_arm64_perf.data')

        # Build binary_cache.
        binary_cache_builder = BinaryCacheBuilder(TestHelper.ndk_path, False)
        binary_cache_builder.build_binary_cache(testdata_file, [TestHelper.testdata_dir])

        # Generate annotated files.
        source_dir = TestHelper.testdata_dir
        self.run_cmd(['annotate.py', '-i', testdata_file, '-s',
                      str(source_dir), '--summary-width', '1000'])

        # Check annotated files.
        annotate_dir = Path('annotated_files')
        summary_file = annotate_dir / 'summary'
        check_items = [
            re.compile(r'100.00% \| 100.00% \| .+two_functions.cpp'),
            '100.00% | 0.00%  | main (line 20)',
            '50.06%  | 50.06% | line 16',
        ]
        self.check_strings_in_file(summary_file, check_items)

        source_files = list(annotate_dir.glob('**/*.cpp'))
        self.assertEqual(len(source_files), 1)
        source_file = source_files[0]
        self.assertEqual(source_file.name, 'two_functions.cpp')
        check_items = ['/* Total 50.06%, Self 50.06%          */    *p = i;']
        self.check_strings_in_file(source_file, check_items)

    def test_sample_filters(self):
        def get_report(filter: str):
            self.run_cmd(['annotate.py', '-i', TestHelper.testdata_path(
                'perf_display_bitmaps.data')] + filter.split())

        get_report('--exclude-pid 31850')
        get_report('--include-pid 31850')
        get_report('--pid 31850')
        get_report('--exclude-tid 31881')
        get_report('--include-tid 31881')
        get_report('--tid 31881')
        get_report('--exclude-process-name com.example.android.displayingbitmaps')
        get_report('--include-process-name com.example.android.displayingbitmaps')
        get_report('--exclude-thread-name com.example.android.displayingbitmaps')
        get_report('--include-thread-name com.example.android.displayingbitmaps')

        with tempfile.NamedTemporaryFile('w', delete=False) as filter_file:
            filter_file.write('GLOBAL_BEGIN 684943449406175\nGLOBAL_END 684943449406176')
            filter_file.flush()
            get_report('--filter-file ' + filter_file.name)
        os.unlink(filter_file.name)

    def test_show_art_frames(self):
        self.run_cmd(
            ['annotate.py', '-i', TestHelper.testdata_path('perf_with_interpreter_frames.data'),
             '--show-art-frames'])
        summary = Path('annotated_files') / 'summary'
        self.check_strings_in_file(summary, 'total period: 9800649')
