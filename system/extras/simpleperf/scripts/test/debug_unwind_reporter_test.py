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

from typing import List

from . test_utils import TestBase, TestHelper


class TestDebugUnwindReporter(TestBase):
    def run_reporter(self, options: List[str]) -> str:
        report_file = TestHelper.testdata_dir / 'debug_unwind_report.txt'
        return self.run_cmd(['debug_unwind_reporter.py', '-i',
                             str(report_file)] + options, return_output=True)

    def test_show_callchain_fixed_by_joiner_option(self):
        output = self.run_reporter([])
        self.assertFalse('__start_thread' in output)
        output = self.run_reporter(['--show-callchain-fixed-by-joiner'])
        self.assertTrue('__start_thread' in output)

    def test_summary_option(self):
        output = self.run_reporter(['--summary'])
        self.assertTrue('Error Code' in output)

    def test_error_code_filter_options(self):
        output = self.run_reporter(['--exclude-error-code', '1'])
        self.assertFalse('sample_time: 626968109563718' in output)
        output = self.run_reporter(
            ['--include-error-code', '4', '--show-callchain-fixed-by-joiner'])
        self.assertFalse('sample_time: 626968109563718' in output)
        self.assertTrue('sample_time: 626970513562864' in output)

    def test_end_dso_filter_options(self):
        output = self.run_reporter(
            ['--exclude-end-dso', '/apex/com.android.runtime/lib64/bionic/libc.so'])
        self.assertFalse('sample_time: 626968109563718' in output)
        output = self.run_reporter(
            ['--include-end-dso', '/apex/com.android.runtime/lib64/bionic/libc.so'])
        self.assertTrue('sample_time: 626968109563718' in output)

    def test_end_symbol_filter_options(self):
        output = self.run_reporter(['--exclude-end-symbol', 'clone'])
        self.assertFalse('sample_time: 626968109563718' in output)
        output = self.run_reporter(
            ['--include-end-symbol', '__start_thread', '--show-callchain-fixed-by-joiner'])
        self.assertFalse('sample_time: 626968109563718' in output)
        self.assertTrue('sample_time: 626970513562864' in output)

    def test_sample_time_filter_options(self):
        output = self.run_reporter(['--exclude-sample-time', '626968109563718'])
        self.assertFalse('sample_time: 626968109563718' in output)
        output = self.run_reporter(
            ['--include-sample-time', '626970513562864', '--show-callchain-fixed-by-joiner'])
        self.assertFalse('sample_time: 626968109563718' in output)
        self.assertTrue('sample_time: 626970513562864' in output)
