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

import glob
import os
from pathlib import Path
import re
import shutil
import subprocess
import time
from typing import List, Tuple

from simpleperf_report_lib import ReportLib
from simpleperf_utils import remove
from . test_utils import TestBase, TestHelper, AdbHelper, INFERNO_SCRIPT


class TestExampleBase(TestBase):
    @classmethod
    def prepare(cls, example_name, package_name, activity_name, abi=None, adb_root=False,
                apk_name: str = 'app-debug.apk'):
        cls.adb = AdbHelper(enable_switch_to_root=adb_root)
        cls.example_path = TestHelper.testdata_path(example_name)
        if not os.path.isdir(cls.example_path):
            log_fatal("can't find " + cls.example_path)
        apk_files = list(Path(cls.example_path).glob(f'**/{apk_name}'))
        if not apk_files:
            log_fatal(f"can't find {apk_name} under " + cls.example_path)
        cls.apk_path = apk_files[0]
        cls.package_name = package_name
        cls.activity_name = activity_name
        args = ["install", "-r"]
        if abi:
            args += ["--abi", abi]
        args.append(cls.apk_path)
        cls.adb.check_run(args)
        cls.adb_root = adb_root
        cls.has_perf_data_for_report = False
        # On Android >= P (version 9), we can profile JITed and interpreted Java code.
        # So only compile Java code on Android <= O (version 8).
        cls.use_compiled_java_code = TestHelper.android_version <= 8
        cls.testcase_dir = TestHelper.get_test_dir(cls.__name__)

    @classmethod
    def tearDownClass(cls):
        if hasattr(cls, 'testcase_dir'):
            remove(cls.testcase_dir)
        if hasattr(cls, 'package_name'):
            cls.adb.check_run(["uninstall", cls.package_name])

    def setUp(self):
        super(TestExampleBase, self).setUp()
        if TestHelper.android_version == 8 and (
                'ExampleJava' in self.id() or 'ExampleKotlin' in self.id()):
            self.skipTest('Profiling java code needs wrap.sh on Android O (8.x)')
        if 'TraceOffCpu' in self.id() and not TestHelper.is_trace_offcpu_supported():
            self.skipTest('trace-offcpu is not supported on device')
        # Use testcase_dir to share a common perf.data for reporting. So we don't need to
        # generate it for each test.
        if not os.path.isdir(self.testcase_dir):
            os.makedirs(self.testcase_dir)
            os.chdir(self.testcase_dir)
            self.run_app_profiler(compile_java_code=self.use_compiled_java_code)
            os.chdir(self.test_dir)

        for name in os.listdir(self.testcase_dir):
            path = os.path.join(self.testcase_dir, name)
            if os.path.isfile(path):
                shutil.copy(path, self.test_dir)
            elif os.path.isdir(path):
                shutil.copytree(path, os.path.join(self.test_dir, name))

    def run(self, result=None):
        self.__class__.test_result = result
        super(TestExampleBase, self).run(result)

    def run_app_profiler(self, record_arg="-g --duration 10", build_binary_cache=True,
                         start_activity=True, compile_java_code=False):
        args = ['app_profiler.py', '--app', self.package_name, '-r', record_arg, '-o', 'perf.data']
        if not build_binary_cache:
            args.append("-nb")
        if compile_java_code:
            args.append('--compile_java_code')
        if start_activity:
            args += ["-a", self.activity_name]
        args += ["-lib", self.example_path]
        if not self.adb_root:
            args.append("--disable_adb_root")
        self.run_cmd(args)
        self.check_exist(filename="perf.data")
        if build_binary_cache:
            self.check_exist(dirname="binary_cache")

    def check_file_under_dir(self, dirname, filename):
        self.check_exist(dirname=dirname)
        for _, _, files in os.walk(dirname):
            for f in files:
                if f == filename:
                    return
        self.fail("Failed to call check_file_under_dir(dir=%s, file=%s)" % (dirname, filename))

    def check_annotation_summary(
            self, summary_file: str, check_entries: List[Tuple[str, float, float]]):
        """ check_entries is a list of (name, accumulated_period, period).
            This function checks for each entry, if the line containing [name]
            has at least required accumulated_period and period.
        """
        self.check_exist(filename=summary_file)
        with open(summary_file, 'r') as fh:
            summary = fh.read()
        fulfilled = [False for x in check_entries]
        summary_check_re = re.compile(r'^\|\s*([\d.]+)%\s*\|\s*([\d.]+)%\s*\|')
        for line in summary.split('\n'):
            for i, (name, need_acc_period, need_period) in enumerate(check_entries):
                if not fulfilled[i] and name in line:
                    m = summary_check_re.search(line)
                    if m:
                        acc_period = float(m.group(1))
                        period = float(m.group(2))
                        if acc_period >= need_acc_period and period >= need_period:
                            fulfilled[i] = True

        self.check_fulfilled_entries(fulfilled, check_entries)

    def check_inferno_report_html(self, check_entries, filename="report.html"):
        self.check_exist(filename=filename)
        with open(filename, 'r') as fh:
            data = fh.read()
        fulfilled = [False for _ in check_entries]
        for line in data.split('\n'):
            # each entry is a (function_name, min_percentage) pair.
            for i, entry in enumerate(check_entries):
                if fulfilled[i] or line.find(entry[0]) == -1:
                    continue
                m = re.search(r'(\d+\.\d+)%', line)
                if m and float(m.group(1)) >= entry[1]:
                    fulfilled[i] = True
                    break
        self.check_fulfilled_entries(fulfilled, check_entries)

    def common_test_app_profiler(self):
        self.run_cmd(["app_profiler.py", "-h"])
        remove("binary_cache")
        self.run_app_profiler(build_binary_cache=False)
        self.assertFalse(os.path.isdir("binary_cache"))
        args = ["binary_cache_builder.py"]
        if not self.adb_root:
            args.append("--disable_adb_root")
        self.run_cmd(args)
        self.check_exist(dirname="binary_cache")
        remove("binary_cache")
        self.run_app_profiler(build_binary_cache=True)
        self.run_app_profiler()
        self.run_app_profiler(start_activity=False)

    def common_test_report(self):
        self.run_cmd(["report.py", "-h"])
        self.run_cmd(["report.py"])
        self.run_cmd(["report.py", "-i", "perf.data"])
        self.run_cmd(["report.py", "-g"])
        self.run_cmd(["report.py", "--self-kill-for-testing", "-g", "--gui"])

    def common_test_annotate(self):
        self.run_cmd(["annotate.py", "-h"])
        remove("annotated_files")
        self.run_cmd(["annotate.py", "-s", self.example_path, '--summary-width', '1000'])
        self.check_exist(dirname="annotated_files")

    def common_test_report_sample(self, check_strings):
        self.run_cmd(["report_sample.py", "-h"])
        self.run_cmd(["report_sample.py"])
        output = self.run_cmd(["report_sample.py", "-i", "perf.data"], return_output=True)
        self.check_strings_in_content(output, check_strings)

    def common_test_pprof_proto_generator(self, check_strings_with_lines,
                                          check_strings_without_lines):
        self.run_cmd(["pprof_proto_generator.py", "-h"])
        self.run_cmd(["pprof_proto_generator.py"])
        remove("pprof.profile")
        self.run_cmd(["pprof_proto_generator.py", "-i", "perf.data", "-o", "pprof.profile"])
        self.check_exist(filename="pprof.profile")
        self.run_cmd(["pprof_proto_generator.py", "--show"])
        output = self.run_cmd(["pprof_proto_generator.py", "--show", "pprof.profile"],
                              return_output=True)
        self.check_strings_in_content(output, check_strings_with_lines + ["has_line_numbers: True"])
        remove("binary_cache")
        self.run_cmd(["pprof_proto_generator.py"])
        output = self.run_cmd(["pprof_proto_generator.py", "--show", "pprof.profile"],
                              return_output=True)
        self.check_strings_in_content(output, check_strings_without_lines +
                                      ["has_line_numbers: False"])

    def common_test_inferno(self):
        self.run_cmd([INFERNO_SCRIPT, "-h"])
        remove("perf.data")
        append_args = [] if self.adb_root else ["--disable_adb_root"]
        self.run_cmd([INFERNO_SCRIPT, "-p", self.package_name, "-t", "3"] + append_args)
        self.check_exist(filename="perf.data")
        self.run_cmd([INFERNO_SCRIPT, "-p", self.package_name, "-f", "1000", "-du", "-t", "1"] +
                     append_args)
        self.run_cmd([INFERNO_SCRIPT, "-p", self.package_name, "-e", "100000 cpu-cycles",
                      "-t", "1"] + append_args)
        self.run_cmd([INFERNO_SCRIPT, "-sc"])

    def common_test_report_html(self):
        self.run_cmd(['report_html.py', '-h'])
        self.run_cmd(['report_html.py'])
        self.run_cmd(['report_html.py', '--add_source_code', '--source_dirs', 'testdata'])
        self.run_cmd(['report_html.py', '--add_disassembly'])
        # Test with multiple perf.data.
        shutil.move('perf.data', 'perf2.data')
        self.run_app_profiler(record_arg='-g -f 1000 --duration 3 -e task-clock:u')
        self.run_cmd(['report_html.py', '-i', 'perf.data', 'perf2.data'])


class TestRecordingRealApps(TestBase):
    def setUp(self):
        super(TestRecordingRealApps, self).setUp()
        self.adb = TestHelper.adb
        self.installed_packages = []

    def tearDown(self):
        for package in self.installed_packages:
            self.adb.run(['shell', 'pm', 'uninstall', package])
        super(TestRecordingRealApps, self).tearDown()

    def install_apk(self, apk_path, package_name):
        self.adb.run(['uninstall', package_name])
        self.adb.run(['install', '-t', apk_path])
        self.installed_packages.append(package_name)

    def start_app(self, start_cmd):
        subprocess.Popen(self.adb.adb_path + ' ' + start_cmd, shell=True,
                         stdout=TestHelper.log_fh, stderr=TestHelper.log_fh)

    def record_data(self, package_name, record_arg):
        self.run_cmd(['app_profiler.py', '--app', package_name, '-r', record_arg])

    def check_symbol_in_record_file(self, symbol_name):
        self.run_cmd(['report.py', '--children', '-o', 'report.txt'])
        self.check_strings_in_file('report.txt', [symbol_name])

    def test_recording_displaybitmaps(self):
        self.install_apk(TestHelper.testdata_path('DisplayBitmaps.apk'),
                         'com.example.android.displayingbitmaps')
        self.install_apk(TestHelper.testdata_path('DisplayBitmapsTest.apk'),
                         'com.example.android.displayingbitmaps.test')
        self.start_app('shell am instrument -w -r -e debug false -e class ' +
                       'com.example.android.displayingbitmaps.tests.GridViewTest ' +
                       'com.example.android.displayingbitmaps.test/' +
                       'androidx.test.runner.AndroidJUnitRunner')
        self.record_data('com.example.android.displayingbitmaps', '-e cpu-clock -g --duration 10')
        if TestHelper.android_version >= 9:
            self.check_symbol_in_record_file('androidx.test.espresso')

    def test_recording_endless_tunnel(self):
        self.install_apk(TestHelper.testdata_path(
            'EndlessTunnel.apk'), 'com.google.sample.tunnel')
        # Test using --launch to start the app.
        self.run_cmd(['app_profiler.py', '--app', 'com.google.sample.tunnel',
                     '--launch', '-r', '-e cpu-clock -g --duration 10'])
        self.check_symbol_in_record_file('PlayScene::DoFrame')

        # Check app versioncode.
        report = ReportLib()
        meta_info = report.MetaInfo()
        self.assertEqual(meta_info.get('app_versioncode'), '1')
        report.Close()
