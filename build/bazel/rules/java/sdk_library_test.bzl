# Copyright (C) 2023 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts")
load(":sdk_library.bzl", "JavaSdkLibraryInfo", "java_sdk_library")

def _basic_java_sdk_library_test_impl(ctx):
    env = analysistest.begin(ctx)
    java_sdk_library_target = analysistest.target_under_test(env)

    asserts.true(
        env,
        java_sdk_library_target[JavaSdkLibraryInfo].public.is_source,
        "Public api surface file should be source, not generated",
    )

    asserts.equals(
        env,
        expected = "public.txt",
        actual = java_sdk_library_target[JavaSdkLibraryInfo].public.basename,
        msg = "Public api surface file not correct",
    )

    asserts.true(
        env,
        java_sdk_library_target[JavaSdkLibraryInfo].system.is_source,
        "System api surface file should be source, not generated",
    )

    asserts.equals(
        env,
        expected = "system.txt",
        actual = java_sdk_library_target[JavaSdkLibraryInfo].system.basename,
        msg = "System api surface file not correct",
    )

    asserts.true(
        env,
        java_sdk_library_target[JavaSdkLibraryInfo].test.is_source,
        "Test api surface file should be source, not generated",
    )

    asserts.equals(
        env,
        expected = "test.txt",
        actual = java_sdk_library_target[JavaSdkLibraryInfo].test.basename,
        msg = "Test api surface file not correct",
    )

    asserts.true(
        env,
        java_sdk_library_target[JavaSdkLibraryInfo].module_lib.is_source,
        "Module_lib api surface file should be source, not generated",
    )

    asserts.equals(
        env,
        expected = "module_lib.txt",
        actual = java_sdk_library_target[JavaSdkLibraryInfo].module_lib.basename,
        msg = "Module_lib api surface file not correct",
    )

    asserts.true(
        env,
        java_sdk_library_target[JavaSdkLibraryInfo].system_server.is_source,
        "System_server api surface file should be source, not generated",
    )

    asserts.equals(
        env,
        expected = "system_server.txt",
        actual = java_sdk_library_target[JavaSdkLibraryInfo].system_server.basename,
        msg = "System_server api surface file not correct",
    )

    return analysistest.end(env)

basic_java_sdk_library_test = analysistest.make(
    _basic_java_sdk_library_test_impl,
)

def test_checked_in_api_surface_files():
    name = "checked_in_api_surface_files_test"
    java_sdk_library(
        name = name + "_target",
        public = "public.txt",
        system = "system.txt",
        test = "test.txt",
        module_lib = "module_lib.txt",
        system_server = "system_server.txt",
    )
    basic_java_sdk_library_test(
        name = name,
        target_under_test = name + "_target",
    )
    return name

def java_sdk_library_test_suite(name):
    native.test_suite(
        name = name,
        tests = [
            test_checked_in_api_surface_files(),
        ],
    )
