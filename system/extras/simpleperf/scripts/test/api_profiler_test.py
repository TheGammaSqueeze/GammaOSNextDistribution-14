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

import logging
import os
import time

from simpleperf_utils import remove
from . test_utils import TestBase, TestHelper


class TestApiProfiler(TestBase):
    def run_api_test(self, package_name, apk_name, expected_reports, min_android_version: int):
        adb = TestHelper.adb
        if TestHelper.android_version < min_android_version:
            logging.info('skip this test on Android < %s.' % min_android_version)
            return
        # step 1: Install and run the app.
        apk_path = TestHelper.testdata_path(apk_name)
        adb.run(['uninstall', package_name])
        adb.check_run(['install', '-t', apk_path])
        # Without sleep, the activity may be killed by post install intent ACTION_PACKAGE_CHANGED.
        time.sleep(3)
        # step 2: Prepare profiling.
        self.run_cmd(['api_profiler.py', 'prepare', '-p', package_name, '-d', '1'])
        if TestHelper.android_version >= 13:
            # Enable perf_harden to check if profile_app_uid property works.
            adb.set_property('security.perf_harden', '1')
        adb.check_run(['shell', 'am', 'start', '-n', package_name + '/.MainActivity'])
        # step 3: Wait until the app exits.
        time.sleep(4)
        while True:
            result = adb.run(['shell', 'pidof', package_name])
            if not result:
                break
            time.sleep(1)
        # step 4: Collect recording data.
        remove('simpleperf_data')
        self.run_cmd(['api_profiler.py', 'collect', '-p', package_name, '-o', 'simpleperf_data'])
        # step 5: Check recording data.
        names = os.listdir('simpleperf_data')
        self.assertGreater(len(names), 0)
        for name in names:
            path = os.path.join('simpleperf_data', name)
            remove('report.txt')
            self.run_cmd(['report.py', '-g', '-o', 'report.txt', '-i', path])
            self.check_strings_in_file('report.txt', expected_reports)
        # step 6: Clean up.
        adb.check_run(['uninstall', package_name])

    def run_cpp_api_test(self, apk_name, min_android_version):
        self.run_api_test('simpleperf.demo.cpp_api', apk_name, ['BusyThreadFunc'],
                          min_android_version)

    def test_cpp_api_on_a_debuggable_app(self):
        # The source code of the apk is in simpleperf/demo/CppApi.
        self.run_cpp_api_test('cpp_api-debuggable.apk', 7)

    def test_cpp_api_on_a_profileable_app(self):
        # a release apk with <profileable android:shell="true" />
        self.run_cpp_api_test('cpp_api-profileable.apk', 10)

    def run_java_api_test(self, apk_name, min_android_version):
        self.run_api_test('simpleperf.demo.java_api', apk_name,
                          ['simpleperf.demo.java_api.MainActivity', 'java.lang.Thread.run'],
                          min_android_version)

    def test_java_api_on_a_debuggable_app(self):
        # The source code of the apk is in simpleperf/demo/JavaApi.
        self.run_java_api_test('java_api-debuggable.apk', 9)

    def test_java_api_on_a_profileable_app(self):
        # a release apk with <profileable android:shell="true" />
        self.run_java_api_test('java_api-profileable.apk', 10)
