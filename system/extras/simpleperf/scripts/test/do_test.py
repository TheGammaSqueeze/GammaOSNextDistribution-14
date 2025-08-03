#!/usr/bin/env python3
#
# Copyright (C) 2017 The Android Open Source Project
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
#
"""Release test for simpleperf prebuilts.

It includes below tests:
1. Test profiling Android apps on different Android versions (starting from Android N).
2. Test simpleperf python scripts on different Hosts (linux, darwin and windows) on x86_64.
3. Test using both devices and emulators.
4. Test using both `adb root` and `adb unroot`.

"""

import argparse
from dataclasses import dataclass
import fnmatch
import inspect
import multiprocessing as mp
import os
from pathlib import Path
import re
import subprocess
import sys
import time
from tqdm import tqdm
import types
from typing import List, Optional
import unittest

from simpleperf_utils import BaseArgumentParser, extant_dir, log_exit, remove, is_darwin

from . api_profiler_test import *
from . annotate_test import *
from . app_profiler_test import *
from . app_test import *
from . binary_cache_builder_test import *
from . cpp_app_test import *
from . debug_unwind_reporter_test import *
from . gecko_profile_generator_test import *
from . inferno_test import *
from . java_app_test import *
from . kotlin_app_test import *
from . pprof_proto_generator_test import *
from . purgatorio_test import *
from . report_html_test import *
from . report_lib_test import *
from . report_sample_test import *
from . run_simpleperf_on_device_test import *
from . sample_filter_test import *
from . stackcollapse_test import *
from . tools_test import *
from . test_utils import TestHelper


def get_args() -> argparse.Namespace:
    parser = BaseArgumentParser(description=__doc__)
    parser.add_argument('--browser', action='store_true', help='open report html file in browser.')
    parser.add_argument(
        '-d', '--device', nargs='+',
        help='set devices used to run tests. Each device in format name:serial-number')
    parser.add_argument('--only-host-test', action='store_true', help='Only run host tests')
    parser.add_argument('--list-tests', action='store_true', help='List tests')
    parser.add_argument('--ndk-path', type=extant_dir, help='Set the path of a ndk release')
    parser.add_argument('-p', '--pattern', nargs='+',
                        help='Run tests matching the selected pattern.')
    parser.add_argument('-r', '--repeat', type=int, default=1, help='times to repeat tests')
    parser.add_argument('--test-from', help='Run tests following the selected test.')
    parser.add_argument('--test-dir', default='test_dir', help='Directory to store test results')
    return parser.parse_args()


def get_all_tests() -> List[str]:
    tests = []
    for name, value in globals().items():
        if isinstance(value, type) and issubclass(value, unittest.TestCase):
            for member_name, member in inspect.getmembers(value):
                if isinstance(member, (types.MethodType, types.FunctionType)):
                    if member_name.startswith('test'):
                        tests.append(name + '.' + member_name)
    return sorted(tests)


def get_host_tests() -> List[str]:
    def filter_fn(test: str) -> bool:
        return get_test_type(test) == 'host_test'
    return list(filter(filter_fn, get_all_tests()))


def get_filtered_tests(
        tests: List[str],
        test_from: Optional[str],
        test_pattern: Optional[List[str]]) -> List[str]:
    if test_from:
        try:
            tests = tests[tests.index(test_from):]
        except ValueError:
            log_exit("Can't find test %s" % test_from)
    if test_pattern:
        patterns = [re.compile(fnmatch.translate(x)) for x in test_pattern]
        tests = [t for t in tests if any(pattern.match(t) for pattern in patterns)]
        if not tests:
            log_exit('No tests are matched.')
    return tests


def get_test_type(test: str) -> Optional[str]:
    testcase_name, test_name = test.split('.')
    if test_name == 'test_run_simpleperf_without_usb_connection':
        return 'device_serialized_test'
    if testcase_name in (
        'TestApiProfiler', 'TestNativeProfiling', 'TestNativeLibDownloader',
            'TestRecordingRealApps', 'TestRunSimpleperfOnDevice'):
        return 'device_test'
    if testcase_name.startswith('TestExample'):
        return 'device_test'
    if testcase_name in ('TestAnnotate',
                         'TestBinaryCacheBuilder',
                         'TestDebugUnwindReporter',
                         'TestInferno',
                         'TestPprofProtoGenerator',
                         'TestProtoFileReportLib',
                         'TestPurgatorio',
                         'TestReportHtml',
                         'TestReportLib',
                         'TestReportSample',
                         'TestSampleFilter',
                         'TestStackCollapse',
                         'TestTools',
                         'TestGeckoProfileGenerator'):
        return 'host_test'
    return None


def build_testdata(testdata_dir: Path):
    """ Collect testdata in testdata_dir.
        In system/extras/simpleperf/scripts, testdata comes from:
            <script_dir>/../testdata, <script_dir>/test/script_testdata, <script_dir>/../demo
        In prebuilts/simpleperf, testdata comes from:
            <script_dir>/test/testdata
    """
    testdata_dir.mkdir()

    script_test_dir = Path(__file__).resolve().parent
    script_dir = script_test_dir.parent

    source_dirs = [
        script_test_dir / 'script_testdata',
        script_test_dir / 'testdata',
        script_dir.parent / 'testdata',
        script_dir.parent / 'demo',
        script_dir.parent / 'runtest',
    ]

    for source_dir in source_dirs:
        if not source_dir.is_dir():
            continue
        for src_path in source_dir.iterdir():
            dest_path = testdata_dir / src_path.name
            if dest_path.exists():
                continue
            if src_path.is_file():
                shutil.copyfile(src_path, dest_path)
            elif src_path.is_dir():
                shutil.copytree(src_path, dest_path)


def run_tests(tests: List[str]) -> bool:
    argv = [sys.argv[0]] + tests
    test_runner = unittest.TextTestRunner(stream=TestHelper.log_fh, verbosity=0)
    test_program = unittest.main(argv=argv, testRunner=test_runner,
                                 exit=False, verbosity=0, module='test.do_test')
    return test_program.result.wasSuccessful()


def test_process_entry(tests: List[str], test_options: List[str], conn: mp.connection.Connection):
    parser = argparse.ArgumentParser()
    parser.add_argument('--browser', action='store_true')
    parser.add_argument('--device', help='android device serial number')
    parser.add_argument('--ndk-path', type=extant_dir)
    parser.add_argument('--testdata-dir', type=extant_dir)
    parser.add_argument('--test-dir', help='directory to store test results')
    args = parser.parse_args(test_options)

    TestHelper.init(args.test_dir, args.testdata_dir,
                    args.browser, args.ndk_path, args.device, conn)
    run_tests(tests)


@dataclass
class Device:
    name: str
    serial_number: str


@dataclass
class TestResult:
    try_time: int
    status: str
    duration: str

    def __str__(self) -> str:
        s = self.status
        if s == 'FAILED':
            s += f' (at try_time {self.try_time})'
        s += f' {self.duration}'
        return s


class TestProcess:
    """ Create a test process to run selected tests on a device. """

    TEST_MAX_TRY_TIME = 10
    TEST_TIMEOUT_IN_SEC = 10 * 60

    def __init__(
            self, test_type: str, tests: List[str],
            device: Optional[Device],
            repeat_index: int,
            test_options: List[str]):
        self.test_type = test_type
        self.tests = tests
        self.device = device
        self.repeat_index = repeat_index
        self.test_options = test_options
        self.try_time = 1
        self.test_results: Dict[str, TestResult] = {}
        self.parent_conn: Optional[mp.connection.Connection] = None
        self.proc: Optional[mp.Process] = None
        self.last_update_time = 0.0
        self._start_test_process()

    def _start_test_process(self):
        unfinished_tests = [test for test in self.tests if test not in self.test_results]
        self.parent_conn, child_conn = mp.Pipe(duplex=False)
        test_options = self.test_options[:]
        test_options += ['--test-dir', str(self.test_dir)]
        if self.device:
            test_options += ['--device', self.device.serial_number]
        self.proc = mp.Process(target=test_process_entry, args=(
            unfinished_tests, test_options, child_conn))
        self.proc.start()
        self.last_update_time = time.time()

    @property
    def name(self) -> str:
        name = self.test_type
        if self.device:
            name += '_' + self.device.name
        name += '_repeat_%d' % self.repeat_index
        return name

    @property
    def test_dir(self) -> Path:
        """ Directory to run the tests. """
        return Path.cwd() / (self.name + '_try_%d' % self.try_time)

    @property
    def alive(self) -> bool:
        """ Return if the test process is alive. """
        return self.proc.is_alive()

    @property
    def finished(self) -> bool:
        """ Return if all tests are finished. """
        return len(self.test_results) == len(self.tests)

    def check_update(self):
        """ Check if there is any test update. """
        try:
            while self.parent_conn.poll():
                msg = self.parent_conn.recv()
                self._process_msg(msg)
                self.last_update_time = time.time()
        except (EOFError, BrokenPipeError) as e:
            pass
        if time.time() - self.last_update_time > TestProcess.TEST_TIMEOUT_IN_SEC:
            self.proc.terminate()

    def _process_msg(self, msg: str):
        test_name, test_success, test_duration = msg.split()
        self.test_results[test_name] = TestResult(self.try_time, test_success, test_duration)

    def join(self):
        self.proc.join()

    def restart(self) -> bool:
        """ Create a new test process to run unfinished tests. """
        if self.finished:
            return False
        if self.try_time == self.TEST_MAX_TRY_TIME:
            """ Exceed max try time. So mark left tests as failed. """
            for test in self.tests:
                if test not in self.test_results:
                    test_duration = '%.3fs' % (time.time() - self.last_update_time)
                    self.test_results[test] = TestResult(self.try_time, 'FAILED', test_duration)
            return False

        self.try_time += 1
        self._start_test_process()
        return True


class ProgressBar:
    def __init__(self, total_count: int):
        self.total_bar = tqdm(
            total=total_count, desc='test progress', ascii=' ##',
            bar_format="{l_bar}{bar}| {n_fmt}/{total_fmt} [{elapsed}, {rate_fmt}", position=0)
        self.test_process_bars: Dict[str, tqdm] = {}

    def update(self, test_proc: TestProcess):
        if test_proc.name not in self.test_process_bars:
            bar = tqdm(total=len(test_proc.tests),
                       desc=test_proc.name, ascii=' ##',
                       bar_format="{l_bar}{bar} | {n_fmt}/{total_fmt} [{elapsed}]")
            self.test_process_bars[test_proc.name] = bar
        else:
            bar = self.test_process_bars[test_proc.name]

        add = len(test_proc.test_results) - bar.n
        if add:
            bar.update(add)
            self.total_bar.update(add)

    def end_test_proc(self, test_proc: TestProcess):
        if test_proc.name in self.test_process_bars:
            self.test_process_bars[test_proc.name].close()
            del self.test_process_bars[test_proc.name]

    def end_tests(self):
        for bar in self.test_process_bars.values():
            bar.close()
        self.total_bar.close()


class TestSummary:
    def __init__(
            self, devices: List[Device],
            device_tests: List[str],
            repeat_count: int, host_tests: List[str]):
        self.results: Dict[Tuple[str, str], Optional[TestResult]] = {}
        for test in device_tests:
            for device in devices:
                for repeat_index in range(1, repeat_count + 1):
                    self.results[(test, '%s_repeat_%d' % (device.name, repeat_index))] = None
        for test in host_tests:
            self.results[(test, 'host')] = None
        self.write_summary()

    @property
    def test_count(self) -> int:
        return len(self.results)

    @property
    def failed_test_count(self) -> int:
        count = 0
        for result in self.results.values():
            if result is None or result.status == 'FAILED':
                count += 1
        return count

    def update(self, test_proc: TestProcess):
        if test_proc.device:
            test_env = '%s_repeat_%d' % (test_proc.device.name, test_proc.repeat_index)
        else:
            test_env = 'host'
        has_update = False
        for test, result in test_proc.test_results.items():
            key = (test, test_env)
            if self.results[key] != result:
                self.results[key] = result
                has_update = True
        if has_update:
            self.write_summary()

    def write_summary(self):
        with open('test_summary.txt', 'w') as fh, \
                open('failed_test_summary.txt', 'w') as failed_fh:
            for key in sorted(self.results.keys()):
                test_name, test_env = key
                result = self.results[key]
                message = f'{test_name}    {test_env}    {result}'
                print(message, file=fh)
                if not result or result.status == 'FAILED':
                    print(message, file=failed_fh)


class TestManager:
    """ Create test processes, monitor their status and log test progresses. """

    def __init__(self, args: argparse.Namespace):
        self.repeat_count = args.repeat
        self.test_options = self._build_test_options(args)
        self.devices = self._build_test_devices(args)
        self.progress_bar: Optional[ProgressBar] = None
        self.test_summary: Optional[TestSummary] = None

    def _build_test_devices(self, args: argparse.Namespace) -> List[Device]:
        devices = []
        if args.device:
            for s in args.device:
                name, serial_number = s.split(':', 1)
                devices.append(Device(name, serial_number))
        else:
            devices.append(Device('default', ''))
        return devices

    def _build_test_options(self, args: argparse.Namespace) -> List[str]:
        test_options: List[str] = []
        if args.browser:
            test_options.append('--browser')
        if args.ndk_path:
            test_options += ['--ndk-path', args.ndk_path]
        testdata_dir = Path('testdata').resolve()
        test_options += ['--testdata-dir', str(testdata_dir)]
        return test_options

    def run_all_tests(self, tests: List[str]):
        device_tests = []
        device_serialized_tests = []
        host_tests = []
        for test in tests:
            test_type = get_test_type(test)
            assert test_type, f'No test type for test {test}'
            if test_type == 'device_test':
                device_tests.append(test)
            if test_type == 'device_serialized_test':
                device_serialized_tests.append(test)
            if test_type == 'host_test':
                host_tests.append(test)
        total_test_count = (len(device_tests) + len(device_serialized_tests)
                            ) * len(self.devices) * self.repeat_count + len(host_tests)
        self.progress_bar = ProgressBar(total_test_count)
        self.test_summary = TestSummary(self.devices, device_tests + device_serialized_tests,
                                        self.repeat_count, host_tests)
        if device_tests:
            self.run_device_tests(device_tests)
        if device_serialized_tests:
            self.run_device_serialized_tests(device_serialized_tests)
        if host_tests:
            self.run_host_tests(host_tests)
        self.progress_bar.end_tests()
        self.progress_bar = None

    def run_device_tests(self, tests: List[str]):
        """ Tests can run in parallel on different devices. """
        test_procs: List[TestProcess] = []
        for device in self.devices:
            test_procs.append(TestProcess('device_test', tests, device, 1, self.test_options))
        self.wait_for_test_results(test_procs, self.repeat_count)

    def run_device_serialized_tests(self, tests: List[str]):
        """ Tests run on each device in order. """
        for device in self.devices:
            test_proc = TestProcess('device_serialized_test', tests, device, 1, self.test_options)
            self.wait_for_test_results([test_proc], self.repeat_count)

    def run_host_tests(self, tests: List[str]):
        """ Tests run only once on host. """
        test_proc = TestProcess('host_tests', tests, None, 1, self.test_options)
        self.wait_for_test_results([test_proc], 1)

    def wait_for_test_results(self, test_procs: List[TestProcess], repeat_count: int):
        test_count = sum(len(test_proc.tests) for test_proc in test_procs)
        while test_procs:
            dead_procs: List[TestProcess] = []
            # Check update.
            for test_proc in test_procs:
                if not test_proc.alive:
                    dead_procs.append(test_proc)
                test_proc.check_update()
                self.progress_bar.update(test_proc)
                self.test_summary.update(test_proc)

            # Process dead procs.
            for test_proc in dead_procs:
                test_proc.join()
                if not test_proc.finished:
                    if test_proc.restart():
                        continue
                    else:
                        self.progress_bar.update(test_proc)
                        self.test_summary.update(test_proc)
                self.progress_bar.end_test_proc(test_proc)
                test_procs.remove(test_proc)
                if test_proc.repeat_index < repeat_count:
                    test_procs.append(
                        TestProcess(test_proc.test_type, test_proc.tests, test_proc.device,
                                    test_proc.repeat_index + 1, test_proc.test_options))
            time.sleep(0.1)
        return True


def run_tests_in_child_process(tests: List[str], args: argparse.Namespace) -> bool:
    """ run tests in child processes, read test results through a pipe. """
    mp.set_start_method('spawn')  # to be consistent on darwin, linux, windows
    test_manager = TestManager(args)
    test_manager.run_all_tests(tests)

    total_test_count = test_manager.test_summary.test_count
    failed_test_count = test_manager.test_summary.failed_test_count
    if failed_test_count == 0:
        print('All tests passed!')
        return True
    print('%d of %d tests failed. See %s/failed_test_summary.txt for details.' %
          (failed_test_count, total_test_count, args.test_dir))
    return False


def main() -> bool:
    args = get_args()
    tests = get_host_tests() if args.only_host_test else get_all_tests()
    tests = get_filtered_tests(tests, args.test_from, args.pattern)

    if args.list_tests:
        print('\n'.join(tests))
        return True

    test_dir = Path(args.test_dir).resolve()
    remove(test_dir)
    test_dir.mkdir(parents=True)
    # Switch to the test dir.
    os.chdir(test_dir)
    build_testdata(Path('testdata'))
    return run_tests_in_child_process(tests, args)
