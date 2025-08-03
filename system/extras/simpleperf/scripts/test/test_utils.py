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
#
"""test_utils.py: utils for testing.
"""

import logging
from multiprocessing.connection import Connection
import os
from pathlib import Path
import re
import shutil
import sys
import subprocess
import time
from typing import List, Optional, Tuple, Union
import unittest

from simpleperf_utils import remove, get_script_dir, AdbHelper, is_windows, bytes_to_str

INFERNO_SCRIPT = str(Path(__file__).parents[1] / ('inferno.bat' if is_windows() else 'inferno.sh'))


class TestHelper:
    """ Keep global test options. """

    @classmethod
    def init(
            cls, test_dir: str, testdata_dir: str, use_browser: bool, ndk_path: Optional[str],
            device_serial_number: Optional[str],
            progress_conn: Optional[Connection]):
        """
            When device_serial_number is None, no Android device is used.
            When device_serial_number is '', use the default Android device.
            When device_serial_number is not empty, select Android device by serial number.
        """
        cls.script_dir = Path(__file__).resolve().parents[1]
        cls.test_base_dir = Path(test_dir).resolve()
        cls.test_base_dir.mkdir(parents=True, exist_ok=True)
        cls.testdata_dir = Path(testdata_dir).resolve()
        cls.browser_option = [] if use_browser else ['--no_browser']
        cls.ndk_path = ndk_path
        cls.progress_conn = progress_conn

        # Logs can come from multiple processes. So use append mode to avoid overwrite.
        cls.log_fh = open(cls.test_base_dir / 'test.log', 'a')
        logging.getLogger().handlers.clear()
        logging.getLogger().addHandler(logging.StreamHandler(cls.log_fh))
        os.close(sys.stderr.fileno())
        os.dup2(cls.log_fh.fileno(), sys.stderr.fileno())

        if device_serial_number is not None:
            if device_serial_number:
                os.environ['ANDROID_SERIAL'] = device_serial_number
            cls.adb = AdbHelper(enable_switch_to_root=True)
            cls.android_version = cls.adb.get_android_version()
            cls.device_features = None

    @classmethod
    def log(cls, s: str):
        cls.log_fh.write(s + '\n')
        # Child processes can also write to log file, so flush it immediately to keep the order.
        cls.log_fh.flush()

    @classmethod
    def testdata_path(cls, testdata_name: str) -> str:
        """ Return the path of a test data. """
        return str(cls.testdata_dir / testdata_name)

    @classmethod
    def get_test_dir(cls, test_name: str) -> Path:
        """ Return the dir to run a test. """
        return cls.test_base_dir / test_name

    @classmethod
    def script_path(cls, script_name: str) -> str:
        """ Return the dir of python scripts. """
        return str(cls.script_dir / script_name)

    @classmethod
    def get_device_features(cls):
        if cls.device_features is None:
            args = [sys.executable, cls.script_path(
                'run_simpleperf_on_device.py'), 'list', '--show-features']
            output = subprocess.check_output(args, stderr=TestHelper.log_fh)
            output = bytes_to_str(output)
            cls.device_features = output.split()
        return cls.device_features

    @classmethod
    def is_trace_offcpu_supported(cls):
        return 'trace-offcpu' in cls.get_device_features()

    @classmethod
    def get_32bit_abi(cls):
        return cls.adb.get_property('ro.product.cpu.abilist32').strip().split(',')[0]

    @classmethod
    def get_kernel_version(cls) -> Tuple[int]:
        output = cls.adb.check_run_and_return_output(['shell', 'uname', '-r'])
        m = re.search(r'^(\d+)\.(\d+)', output)
        assert m
        return (int(m.group(1)), int(m.group(2)))

    @classmethod
    def write_progress(cls, progress: str):
        if cls.progress_conn:
            cls.progress_conn.send(progress)


class TestBase(unittest.TestCase):
    def setUp(self):
        """ Run each test in a separate dir. """
        self.test_dir = TestHelper.get_test_dir(
            '%s.%s' % (self.__class__.__name__, self._testMethodName))
        self.test_dir.mkdir()
        os.chdir(self.test_dir)
        TestHelper.log('begin test %s.%s' % (self.__class__.__name__, self._testMethodName))

    def run(self, result=None):
        start_time = time.time()
        ret = super(TestBase, self).run(result)
        if result.errors and result.errors[-1][0] == self:
            status = 'FAILED'
            err_info = result.errors[-1][1]
        elif result.failures and result.failures[-1][0] == self:
            status = 'FAILED'
            err_info = result.failures[-1][1]
        elif result.skipped and result.skipped[-1][0] == self:
            status = 'SKIPPED'
        else:
            status = 'OK'

        time_taken = time.time() - start_time
        TestHelper.log(
            'end test %s.%s %s (%.3fs)' %
            (self.__class__.__name__, self._testMethodName, status, time_taken))
        if status == 'FAILED':
            TestHelper.log(err_info)

        # Remove test data for passed tests to save space.
        if status == 'OK':
            remove(self.test_dir)
        TestHelper.write_progress(
            '%s.%s  %s  %.3fs' %
            (self.__class__.__name__, self._testMethodName, status, time_taken))
        return ret

    def run_cmd(self, args: List[str], return_output=False, drop_output=True) -> str:
        if args[0] == 'report_html.py' or args[0] == INFERNO_SCRIPT:
            args += TestHelper.browser_option
        if TestHelper.ndk_path:
            if args[0] in ['app_profiler.py', 'binary_cache_builder.py', 'pprof_proto_generator.py',
                           'report_html.py', 'annotate.py']:
                args += ['--ndk_path', TestHelper.ndk_path]
        if args[0].endswith('.py'):
            args = [sys.executable, TestHelper.script_path(args[0])] + args[1:]
        use_shell = args[0].endswith('.bat')
        try:
            if return_output:
                stdout_fd = subprocess.PIPE
                drop_output = False
            elif drop_output:
                stdout_fd = subprocess.DEVNULL
            else:
                stdout_fd = None

            subproc = subprocess.Popen(args, stdout=stdout_fd,
                                       stderr=TestHelper.log_fh, shell=use_shell)
            stdout_data, _ = subproc.communicate()
            output_data = bytes_to_str(stdout_data)
            returncode = subproc.returncode

        except OSError:
            returncode = None
        self.assertEqual(returncode, 0, msg="failed to run cmd: %s" % args)
        if return_output:
            return output_data
        return ''

    def check_strings_in_file(self, filename, strings: List[Union[str, re.Pattern]]):
        self.check_exist(filename=filename)
        with open(filename, 'r') as fh:
            self.check_strings_in_content(fh.read(), strings)

    def check_exist(self, filename=None, dirname=None):
        if filename:
            self.assertTrue(os.path.isfile(filename), filename)
        if dirname:
            self.assertTrue(os.path.isdir(dirname), dirname)

    def check_strings_in_content(self, content: str, strings: List[Union[str, re.Pattern]]):
        fulfilled = []
        for s in strings:
            if isinstance(s, re.Pattern):
                fulfilled.append(s.search(content))
            else:
                fulfilled.append(s in content)
        self.check_fulfilled_entries(fulfilled, strings)

    def check_fulfilled_entries(self, fulfilled, entries):
        failed_entries = []
        for ok, entry in zip(fulfilled, entries):
            if not ok:
                failed_entries.append(entry)

        if failed_entries:
            self.fail('failed in below entries: %s' % (failed_entries,))
