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

from app_profiler import NativeLibDownloader
import shutil
import subprocess
import sys

from simpleperf_utils import str_to_bytes, bytes_to_str, remove
from . test_utils import TestBase, TestHelper, INFERNO_SCRIPT


class TestNativeProfiling(TestBase):
    def setUp(self):
        super(TestNativeProfiling, self).setUp()
        self.is_rooted_device = TestHelper.adb.switch_to_root()

    def test_profile_cmd(self):
        self.run_cmd(["app_profiler.py", "-cmd", "pm -l", "--disable_adb_root"])
        self.run_cmd(["report.py", "-g", "-o", "report.txt"])

    def test_profile_native_program(self):
        if not self.is_rooted_device:
            return
        self.run_cmd(["app_profiler.py", "-np", "surfaceflinger"])
        self.run_cmd(["report.py", "-g", "-o", "report.txt"])
        self.run_cmd([INFERNO_SCRIPT, "-sc"])
        self.run_cmd([INFERNO_SCRIPT, "-np", "surfaceflinger"])

    def test_profile_pids(self):
        if not self.is_rooted_device:
            return
        pid = int(TestHelper.adb.check_run_and_return_output(['shell', 'pidof', 'system_server']))
        self.run_cmd(['app_profiler.py', '--pid', str(pid), '-r', '--duration 1'])
        self.run_cmd(['app_profiler.py', '--pid', str(pid), str(pid), '-r', '--duration 1'])
        self.run_cmd(['app_profiler.py', '--tid', str(pid), '-r', '--duration 1'])
        self.run_cmd(['app_profiler.py', '--tid', str(pid), str(pid), '-r', '--duration 1'])
        self.run_cmd([INFERNO_SCRIPT, '--pid', str(pid), '-t', '1'])

    def test_profile_system_wide(self):
        if not self.is_rooted_device:
            return
        self.run_cmd(['app_profiler.py', '--system_wide', '-r', '--duration 1'])

    def test_device_not_connected(self):
        args = [sys.executable, TestHelper.script_path('app_profiler.py'), '-cmd', 'ls']
        proc = subprocess.run(
            args, env={'ANDROID_SERIAL': 'not_exist_device'},
            stderr=subprocess.PIPE, text=True)
        self.assertIn('No Android device is connected via ADB.', proc.stderr)


class TestNativeLibDownloader(TestBase):
    def setUp(self):
        super(TestNativeLibDownloader, self).setUp()
        self.adb = TestHelper.adb
        self.adb.check_run(['shell', 'rm', '-rf', '/data/local/tmp/native_libs'])
        self.ndk_path = TestHelper.ndk_path

    def tearDown(self):
        self.adb.check_run(['shell', 'rm', '-rf', '/data/local/tmp/native_libs'])
        super(TestNativeLibDownloader, self).tearDown()

    def list_lib_on_device(self, path):
        result, output = self.adb.run_and_return_output(['shell', 'ls', '-llc', path])
        return output if result else ''

    def test_smoke(self):
        # Sync all native libs on device.
        downloader = NativeLibDownloader(self.ndk_path, 'arm64', self.adb)
        downloader.collect_native_libs_on_host(TestHelper.testdata_path(
            'SimpleperfExampleCpp/app/build/intermediates/cmake/debug'))
        self.assertEqual(len(downloader.host_build_id_map), 2)
        for entry in downloader.host_build_id_map.values():
            self.assertEqual(entry.score, 3)
        downloader.collect_native_libs_on_device()
        self.assertEqual(len(downloader.device_build_id_map), 0)

        lib_list = list(downloader.host_build_id_map.items())
        for sync_count in [0, 1, 2]:
            build_id_map = {}
            for i in range(sync_count):
                build_id_map[lib_list[i][0]] = lib_list[i][1]
            downloader.host_build_id_map = build_id_map
            downloader.sync_native_libs_on_device()
            downloader.collect_native_libs_on_device()
            self.assertEqual(len(downloader.device_build_id_map), sync_count)
            for i, item in enumerate(lib_list):
                build_id = item[0]
                name = item[1].name
                if i < sync_count:
                    self.assertTrue(build_id in downloader.device_build_id_map)
                    self.assertEqual(name, downloader.device_build_id_map[build_id])
                    self.assertTrue(self.list_lib_on_device(downloader.dir_on_device + name))
                else:
                    self.assertTrue(build_id not in downloader.device_build_id_map)
                    self.assertFalse(self.list_lib_on_device(downloader.dir_on_device + name))
            if sync_count == 1:
                self.adb.run(['pull', '/data/local/tmp/native_libs/build_id_list',
                              'build_id_list'])
                with open('build_id_list', 'rb') as fh:
                    self.assertEqual(bytes_to_str(fh.read()),
                                     '{}={}\n'.format(lib_list[0][0], lib_list[0][1].name))
                remove('build_id_list')

    def test_handle_wrong_build_id_list(self):
        with open('build_id_list', 'wb') as fh:
            fh.write(str_to_bytes('fake_build_id=binary_not_exist\n'))
        self.adb.check_run(['shell', 'mkdir', '-p', '/data/local/tmp/native_libs'])
        self.adb.check_run(['push', 'build_id_list', '/data/local/tmp/native_libs'])
        remove('build_id_list')
        downloader = NativeLibDownloader(self.ndk_path, 'arm64', self.adb)
        downloader.collect_native_libs_on_device()
        self.assertEqual(len(downloader.device_build_id_map), 0)

    def test_download_file_without_build_id(self):
        downloader = NativeLibDownloader(self.ndk_path, 'x86_64', self.adb)
        name = 'elf.so'
        shutil.copyfile(TestHelper.testdata_path('data/symfs_without_build_id/elf'), name)
        downloader.collect_native_libs_on_host('.')
        downloader.collect_native_libs_on_device()
        self.assertIn(name, downloader.no_build_id_file_map)
        # Check if file without build id can be downloaded.
        downloader.sync_native_libs_on_device()
        target_file = downloader.dir_on_device + name
        target_file_stat = self.list_lib_on_device(target_file)
        self.assertTrue(target_file_stat)

        # No need to re-download if file size doesn't change.
        downloader.sync_native_libs_on_device()
        self.assertEqual(target_file_stat, self.list_lib_on_device(target_file))

        # Need to re-download if file size changes.
        self.adb.check_run(['shell', 'truncate', '-s', '0', target_file])
        target_file_stat = self.list_lib_on_device(target_file)
        downloader.sync_native_libs_on_device()
        self.assertNotEqual(target_file_stat, self.list_lib_on_device(target_file))
