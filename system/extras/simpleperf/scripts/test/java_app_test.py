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
import signal
import subprocess
import sys
import time
import unittest

from simpleperf_utils import is_windows, remove
from . app_test import TestExampleBase
from . test_utils import TestHelper, INFERNO_SCRIPT


class TestExampleJava(TestExampleBase):
    @classmethod
    def setUpClass(cls):
        cls.prepare("SimpleperfExampleJava",
                    "simpleperf.example.java",
                    ".MainActivity")

    def test_app_profiler(self):
        self.common_test_app_profiler()

    def test_app_profiler_profile_from_launch(self):
        self.run_app_profiler(start_activity=True, build_binary_cache=False)
        self.run_cmd(["report.py", "-g", "-o", "report.txt"])
        self.check_strings_in_file("report.txt", [
            "simpleperf.example.java.MainActivity$1.run",
            "__start_thread"])

    def test_app_profiler_multiprocesses(self):
        self.adb.check_run(['shell', 'am', 'force-stop', self.package_name])
        self.adb.check_run(['shell', 'am', 'start', '-n',
                            self.package_name + '/.MultiProcessActivity'])
        # Wait until both MultiProcessActivity and MultiProcessService set up.
        time.sleep(3)
        self.run_app_profiler(start_activity=False)
        self.run_cmd(["report.py", "-o", "report.txt"])
        self.check_strings_in_file("report.txt", ["BusyService", "BusyThread"])

    def test_app_profiler_with_ctrl_c(self):
        if is_windows():
            return
        self.adb.check_run(['shell', 'am', 'start', '-n', self.package_name + '/.MainActivity'])
        time.sleep(1)
        args = [sys.executable, TestHelper.script_path("app_profiler.py"),
                "--app", self.package_name, "-r", "--duration 10000", "--disable_adb_root"]
        if TestHelper.ndk_path:
            args += ['--ndk_path', TestHelper.ndk_path]
        subproc = subprocess.Popen(args)
        time.sleep(3)

        subproc.send_signal(signal.SIGINT)
        subproc.wait()
        self.assertEqual(subproc.returncode, 0)
        self.run_cmd(["report.py"])

    def test_app_profiler_stop_after_app_exit(self):
        self.adb.check_run(['shell', 'am', 'start', '-n', self.package_name + '/.MainActivity'])
        time.sleep(1)
        args = [sys.executable, TestHelper.script_path('app_profiler.py'),
                '--app', self.package_name, '-r', '--duration 10000', '--disable_adb_root']
        if TestHelper.ndk_path:
            args += ['--ndk_path', TestHelper.ndk_path]
        subproc = subprocess.Popen(args)
        time.sleep(3)
        self.adb.check_run(['shell', 'am', 'force-stop', self.package_name])
        subproc.wait()
        self.assertEqual(subproc.returncode, 0)
        self.run_cmd(["report.py"])

    def test_app_profiler_with_ndk_path(self):
        # Although we pass an invalid ndk path, it should be able to find tools in default ndk path.
        self.run_cmd(['app_profiler.py', '--app', self.package_name, '-a', self.activity_name,
                      '--ndk_path', '.'])

    def test_report(self):
        self.common_test_report()
        self.run_cmd(["report.py", "-g", "-o", "report.txt"])
        self.check_strings_in_file("report.txt", [
            "simpleperf.example.java.MainActivity$1.run",
            "__start_thread"])

    def test_profile_with_process_id(self):
        self.adb.check_run(['shell', 'am', 'start', '-n', self.package_name + '/.MainActivity'])
        time.sleep(1)
        pid = self.adb.check_run_and_return_output(
            ['shell', 'pidof', 'simpleperf.example.java']).strip()
        self.run_app_profiler(start_activity=False, record_arg='-g --duration 10 -p ' + pid)
        self.run_cmd(["report.py", "-g", "-o", "report.txt"])
        self.check_strings_in_file("report.txt", [
            "simpleperf.example.java.MainActivity$1.run",
            "__start_thread"])

    def test_annotate(self):
        self.common_test_annotate()
        if not self.use_compiled_java_code:
            # Currently annotating Java code is only supported when the Java code is compiled.
            return
        self.check_file_under_dir("annotated_files", "MainActivity.java")
        summary_file = os.path.join("annotated_files", "summary")
        self.check_annotation_summary(summary_file, [
            ("MainActivity.java", 80, 80),
            ("run", 80, 0),
            ("callFunction", 0, 0),
            ("line 23", 80, 0)])

    def test_report_sample(self):
        self.common_test_report_sample(
            ["simpleperf.example.java.MainActivity$1.run",
             "__start_thread"])

    def test_pprof_proto_generator(self):
        check_strings_with_lines = []
        if self.use_compiled_java_code:
            check_strings_with_lines = [
                "simpleperf/example/java/MainActivity.java",
                "run"]
        self.common_test_pprof_proto_generator(
            check_strings_with_lines=check_strings_with_lines,
            check_strings_without_lines=["simpleperf.example.java.MainActivity$1.run"])

    def test_inferno(self):
        self.common_test_inferno()
        self.run_app_profiler()
        self.run_cmd([INFERNO_SCRIPT, "-sc"])
        self.check_inferno_report_html(
            [('simpleperf.example.java.MainActivity$1.run', 80)])
        self.run_cmd([INFERNO_SCRIPT, "-sc", "-o", "report2.html"])
        self.check_inferno_report_html(
            [('simpleperf.example.java.MainActivity$1.run', 80)],
            "report2.html")

    def test_inferno_in_another_dir(self):
        test_dir = 'inferno_testdir'
        os.mkdir(test_dir)
        os.chdir(test_dir)
        self.run_cmd(['app_profiler.py', '--app', self.package_name,
                      '-r', '-e task-clock:u -g --duration 3'])
        self.check_exist(filename="perf.data")
        self.run_cmd([INFERNO_SCRIPT, "-sc"])

    def test_report_html(self):
        self.common_test_report_html()

    def test_run_simpleperf_without_usb_connection(self):
        self.adb.check_run(['shell', 'am', 'start', '-n', self.package_name + '/.MainActivity'])
        self.run_cmd(['run_simpleperf_without_usb_connection.py', 'start', '-p',
                      self.package_name, '--size_limit', '1M'])
        self.adb.check_run(['kill-server'])
        time.sleep(3)
        # Start adb process outside self.test_dir. Because it will be removed after testing.
        os.chdir(self.test_dir.parent)
        self.adb.check_run(['devices'])
        os.chdir(self.test_dir)
        self.run_cmd(['run_simpleperf_without_usb_connection.py', 'stop'])
        self.check_exist(filename="perf.data")
        self.run_cmd(["report.py", "-g", "-o", "report.txt"])


class TestExampleJavaProfileableApk(TestExampleJava):
    """ Test profiling a profileable released apk."""
    @classmethod
    def setUpClass(cls):
        if TestHelper.android_version >= 10:
            cls.prepare("SimpleperfExampleJava",
                        "simpleperf.example.java",
                        ".MainActivity", apk_name='app-release.apk')

    def setUp(self):
        if TestHelper().android_version < 10:
            raise unittest.SkipTest("Profileable apk isn't supported on Android < Q.")
        super().setUp()


class TestExampleJavaRoot(TestExampleBase):
    @classmethod
    def setUpClass(cls):
        cls.prepare("SimpleperfExampleJava",
                    "simpleperf.example.java",
                    ".MainActivity",
                    adb_root=True)

    def test_app_profiler(self):
        self.common_test_app_profiler()


class TestExampleJavaTraceOffCpu(TestExampleBase):
    @classmethod
    def setUpClass(cls):
        cls.prepare("SimpleperfExampleJava",
                    "simpleperf.example.java",
                    ".SleepActivity")

    def test_smoke(self):
        self.run_app_profiler(record_arg="-g -f 1000 --duration 10 -e cpu-clock:u --trace-offcpu")
        self.run_cmd(["report.py", "-g", "-o", "report.txt"])
        self.check_strings_in_file("report.txt", [
            "simpleperf.example.java.SleepActivity$1.run",
            "simpleperf.example.java.SleepActivity$1.RunFunction",
            "simpleperf.example.java.SleepActivity$1.SleepFunction"
        ])
        remove("annotated_files")
        self.run_cmd(["annotate.py", "-s", self.example_path, '--summary-width', '1000'])
        self.check_exist(dirname="annotated_files")
        if self.use_compiled_java_code:
            self.check_file_under_dir("annotated_files", "SleepActivity.java")
            summary_file = os.path.join("annotated_files", "summary")
            self.check_annotation_summary(summary_file, [
                ("SleepActivity.java", 80, 20),
                ("run", 80, 0),
                ("RunFunction", 20, 20),
                ("SleepFunction", 20, 0),
                ("line 24", 1, 0),
                ("line 31", 20, 0)])
        self.run_cmd([INFERNO_SCRIPT, "-sc"])
        self.check_inferno_report_html(
            [('simpleperf.example.java.SleepActivity$1.run', 80),
             ('simpleperf.example.java.SleepActivity$1.RunFunction', 20),
             ('simpleperf.example.java.SleepActivity$1.SleepFunction', 20)])
