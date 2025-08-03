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
import unittest

from simpleperf_utils import remove
from . app_test import TestExampleBase
from . test_utils import INFERNO_SCRIPT, TestHelper


class TestExampleKotlin(TestExampleBase):
    @classmethod
    def setUpClass(cls):
        cls.prepare("SimpleperfExampleKotlin",
                    "simpleperf.example.kotlin",
                    ".MainActivity")

    def test_app_profiler(self):
        self.common_test_app_profiler()

    def test_app_profiler_profile_from_launch(self):
        self.run_app_profiler(start_activity=True, build_binary_cache=False)
        self.run_cmd(["report.py", "-g", "-o", "report.txt"])
        self.check_strings_in_file("report.txt", [
            "simpleperf.example.kotlin.MainActivity$createBusyThread$1." +
            "run", "__start_thread"])

    def test_report(self):
        self.common_test_report()
        self.run_cmd(["report.py", "-g", "-o", "report.txt"])
        self.check_strings_in_file("report.txt", [
            "simpleperf.example.kotlin.MainActivity$createBusyThread$1." +
            "run", "__start_thread"])

    def test_annotate(self):
        if not self.use_compiled_java_code:
            return
        self.common_test_annotate()
        self.check_file_under_dir("annotated_files", "MainActivity.kt")
        summary_file = os.path.join("annotated_files", "summary")
        self.check_annotation_summary(summary_file, [
            ("MainActivity.kt", 80, 80),
            ("run", 80, 0),
            ("callFunction", 0, 0),
            ("line 19", 80, 0),
            ("line 25", 0, 0)])

    def test_report_sample(self):
        self.common_test_report_sample([
            "simpleperf.example.kotlin.MainActivity$createBusyThread$1." +
            "run", "__start_thread"])

    def test_pprof_proto_generator(self):
        check_strings_with_lines = []
        if self.use_compiled_java_code:
            check_strings_with_lines = [
                "simpleperf/example/kotlin/MainActivity.kt",
                "run"]
        self.common_test_pprof_proto_generator(
            check_strings_with_lines=check_strings_with_lines,
            check_strings_without_lines=[
                'simpleperf.example.kotlin.MainActivity$createBusyThread$1.run'])

    def test_inferno(self):
        self.common_test_inferno()
        self.run_app_profiler()
        self.run_cmd([INFERNO_SCRIPT, "-sc"])
        self.check_inferno_report_html([
            ('simpleperf.example.kotlin.MainActivity$createBusyThread$1.run', 80)])

    def test_report_html(self):
        self.common_test_report_html()


class TestExampleKotlinProfileableApk(TestExampleKotlin):
    """ Test profiling a profileable released apk."""
    @classmethod
    def setUpClass(cls):
        if TestHelper.android_version >= 10:
            cls.prepare("SimpleperfExampleKotlin",
                        "simpleperf.example.kotlin",
                        ".MainActivity", apk_name='app-release.apk')

    def setUp(self):
        if TestHelper().android_version < 10:
            raise unittest.SkipTest("Profileable apk isn't supported on Android < Q.")
        super().setUp()


class TestExampleKotlinRoot(TestExampleBase):
    @classmethod
    def setUpClass(cls):
        cls.prepare("SimpleperfExampleKotlin",
                    "simpleperf.example.kotlin",
                    ".MainActivity",
                    adb_root=True)

    def test_app_profiler(self):
        self.common_test_app_profiler()


class TestExampleKotlinTraceOffCpu(TestExampleBase):
    @classmethod
    def setUpClass(cls):
        cls.prepare("SimpleperfExampleKotlin",
                    "simpleperf.example.kotlin",
                    ".SleepActivity")

    def test_smoke(self):
        self.run_app_profiler(record_arg="-g -f 1000 --duration 10 -e cpu-clock:u --trace-offcpu")
        self.run_cmd(["report.py", "-g", "-o", "report.txt"])
        function_prefix = 'simpleperf.example.kotlin.SleepActivity$createRunSleepThread$1.'
        self.check_strings_in_file("report.txt", [
            function_prefix + "run",
            function_prefix + "RunFunction",
            function_prefix + "SleepFunction"
        ])
        if self.use_compiled_java_code:
            remove("annotated_files")
            self.run_cmd(["annotate.py", "-s", self.example_path, '--summary-width', '1000'])
            self.check_exist(dirname="annotated_files")
            self.check_file_under_dir("annotated_files", "SleepActivity.kt")
            summary_file = os.path.join("annotated_files", "summary")
            self.check_annotation_summary(summary_file, [
                ("SleepActivity.kt", 80, 20),
                ("run", 80, 0),
                ("RunFunction", 20, 20),
                ("SleepFunction", 20, 0),
                ("line 23", 20, 0),
                ("line 31", 20, 0)])

        self.run_cmd([INFERNO_SCRIPT, "-sc"])
        self.check_inferno_report_html([
            (function_prefix + 'run', 80),
            (function_prefix + 'RunFunction', 20),
            (function_prefix + 'SleepFunction', 20)])
