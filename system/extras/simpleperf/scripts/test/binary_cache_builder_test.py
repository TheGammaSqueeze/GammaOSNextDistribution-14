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

import filecmp
import os
from pathlib import Path
import shutil
import tempfile
import zipfile

from binary_cache_builder import BinaryCacheBuilder
from simpleperf_utils import ReadElf, remove, ToolFinder
from . test_utils import TestBase, TestHelper


class TestBinaryCacheBuilder(TestBase):
    def test_copy_binaries_from_symfs_dirs(self):
        readelf = ReadElf(TestHelper.ndk_path)
        strip = ToolFinder.find_tool_path('llvm-strip', ndk_path=TestHelper.ndk_path, arch='arm')
        self.assertIsNotNone(strip)
        symfs_dir = os.path.join(self.test_dir, 'symfs_dir')
        remove(symfs_dir)
        os.mkdir(symfs_dir)
        filename = 'simpleperf_runtest_two_functions_arm'
        origin_file = TestHelper.testdata_path(filename)
        source_file = os.path.join(symfs_dir, filename)
        build_id = readelf.get_build_id(origin_file)
        binary_cache_builder = BinaryCacheBuilder(TestHelper.ndk_path, False)
        binary_cache_builder.binaries['simpleperf_runtest_two_functions_arm'] = build_id

        # Copy binary if target file doesn't exist.
        target_file = binary_cache_builder.find_path_in_cache(filename)
        remove(target_file)
        self.run_cmd([strip, '--strip-all', '-o', source_file, origin_file])
        binary_cache_builder.copy_binaries_from_symfs_dirs([symfs_dir])
        self.assertTrue(filecmp.cmp(target_file, source_file))

        # Copy binary if target file doesn't have .symtab and source file has .symtab.
        self.run_cmd([strip, '--strip-debug', '-o', source_file, origin_file])
        binary_cache_builder.copy_binaries_from_symfs_dirs([symfs_dir])
        self.assertTrue(filecmp.cmp(target_file, source_file))

        # Copy binary if target file doesn't have .debug_line and source_files has .debug_line.
        shutil.copy(origin_file, source_file)
        binary_cache_builder.copy_binaries_from_symfs_dirs([symfs_dir])
        self.assertTrue(filecmp.cmp(target_file, source_file))

    def test_copy_elf_without_build_id_from_symfs_dir(self):
        binary_cache_builder = BinaryCacheBuilder(TestHelper.ndk_path, False)
        binary_cache_builder.binaries['elf'] = ''
        symfs_dir = TestHelper.testdata_path('data/symfs_without_build_id')
        source_file = os.path.join(symfs_dir, 'elf')
        target_file = binary_cache_builder.find_path_in_cache('elf')
        binary_cache_builder.copy_binaries_from_symfs_dirs([symfs_dir])
        self.assertTrue(filecmp.cmp(target_file, source_file))
        binary_cache_builder.pull_binaries_from_device()
        self.assertTrue(filecmp.cmp(target_file, source_file))

    def test_copy_binary_with_different_name(self):
        # Build symfs_dir.
        symfs_dir = self.test_dir / 'symfs_dir'
        remove(symfs_dir)
        symfs_dir.mkdir()
        filename = 'simpleperf_runtest_two_functions_arm'
        origin_file = TestHelper.testdata_path(filename)
        modified_name = 'two_functions_arm'
        source_file = os.path.join(symfs_dir, modified_name)
        shutil.copy(origin_file, source_file)

        # Copy binary with the same build id but a different name.
        builder = BinaryCacheBuilder(TestHelper.ndk_path, False)
        builder.binaries[filename] = builder.readelf.get_build_id(origin_file)
        builder.copy_binaries_from_symfs_dirs([symfs_dir])

        target_file = builder.find_path_in_cache(filename)
        self.assertTrue(filecmp.cmp(target_file, source_file))

    def test_copy_binary_for_native_lib_embedded_in_apk(self):
        apk_path = TestHelper.testdata_path('data/app/com.example.hellojni-1/base.apk')
        symfs_dir = self.test_dir / 'symfs_dir'
        with zipfile.ZipFile(apk_path, 'r') as zip_ref:
            zip_ref.extractall(symfs_dir)
        builder = BinaryCacheBuilder(TestHelper.ndk_path, False)
        builder.collect_used_binaries(
            TestHelper.testdata_path('has_embedded_native_libs_apk_perf.data'))
        builder.copy_binaries_from_symfs_dirs([symfs_dir])

        device_path = [p for p in builder.binaries if 'libhello-jni.so' in p][0]
        target_file = builder.find_path_in_cache(device_path)
        self.assertTrue(target_file.is_file())
        # Check that we are not using path format of embedded lib in apk. Because
        # simpleperf can't use it from binary_cache.
        self.assertNotIn('!/', str(target_file))

    def test_prefer_binary_with_debug_info(self):
        binary_cache_builder = BinaryCacheBuilder(TestHelper.ndk_path, False)
        binary_cache_builder.collect_used_binaries(
            TestHelper.testdata_path('runtest_two_functions_arm64_perf.data'))
        filename = 'simpleperf_runtest_two_functions_arm64'

        # Create a symfs_dir, which contains elf file with and without debug info.
        with tempfile.TemporaryDirectory() as tmp_dir:
            shutil.copy(
                TestHelper.testdata_path(
                    'simpleperf_runtest_two_functions_arm64_without_debug_info'),
                Path(tmp_dir) / filename)

            debug_dir = Path(tmp_dir) / 'debug'
            debug_dir.mkdir()
            debug_file = TestHelper.testdata_path(filename)
            shutil.copy(debug_file, debug_dir)
            # Check if the elf file with debug info is chosen.
            binary_cache_builder.copy_binaries_from_symfs_dirs([tmp_dir])
            target_file = binary_cache_builder.find_path_in_cache('/data/local/tmp/' + filename)
            self.assertTrue(filecmp.cmp(target_file, debug_file))

    def test_create_build_id_list(self):
        symfs_dir = TestHelper.testdata_dir
        binary_cache_builder = BinaryCacheBuilder(TestHelper.ndk_path, False)
        binary_cache_builder.collect_used_binaries(
            TestHelper.testdata_path('runtest_two_functions_arm64_perf.data'))
        binary_cache_builder.copy_binaries_from_symfs_dirs([symfs_dir])

        target_file = binary_cache_builder.find_path_in_cache(
            '/data/local/tmp/simpleperf_runtest_two_functions_arm64')
        self.assertTrue(target_file.is_file())

        binary_cache_builder.create_build_id_list()
        build_id_list_path = Path(binary_cache_builder.binary_cache_dir) / 'build_id_list'
        self.assertTrue(build_id_list_path.is_file())
        with open(build_id_list_path, 'r') as fh:
            self.assertIn('simpleperf_runtest_two_functions_arm64', fh.read())
