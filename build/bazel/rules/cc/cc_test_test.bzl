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

load(":cc_library_common_test.bzl", "target_provides_androidmk_info_test")
load(":cc_library_shared.bzl", "cc_library_shared")
load(":cc_library_static.bzl", "cc_library_static")
load(":cc_test.bzl", "cc_test")

def _cc_test_provides_androidmk_info():
    name = "cc_test_provides_androidmk_info"
    dep_name = name + "_static_dep"
    whole_archive_dep_name = name + "_whole_archive_dep"
    dynamic_dep_name = name + "_dynamic_dep"

    srcs = ["//build/bazel/rules/cc/testing:test_srcs"]
    gunit_test_srcs = ["//build/bazel/rules/cc/testing:gunit_test_srcs"]

    cc_library_static(
        name = dep_name,
        srcs = srcs,
        tags = ["manual"],
    )
    cc_library_static(
        name = whole_archive_dep_name,
        srcs = srcs,
        tags = ["manual"],
    )
    cc_library_shared(
        name = dynamic_dep_name,
        srcs = srcs,
        tags = ["manual"],
    )
    cc_test(
        name = name,
        srcs = gunit_test_srcs,
        deps = [dep_name],
        whole_archive_deps = [whole_archive_dep_name],
        dynamic_deps = [dynamic_dep_name],
        runs_on = ["host_without_device", "device"],
        target_compatible_with = ["//build/bazel_common_rules/platforms/os:linux"],
        tags = ["manual"],
    )
    android_test_name = name + "_android"
    linux_test_name = name + "_linux"
    target_provides_androidmk_info_test(
        name = android_test_name,
        target_under_test = name,
        expected_static_libs = [dep_name, "libgtest_main", "libgtest", "libc++demangle", "libunwind"],
        expected_whole_static_libs = [whole_archive_dep_name],
        expected_shared_libs = [dynamic_dep_name, "libc++", "libc", "libdl", "libm"],
        target_compatible_with = ["//build/bazel_common_rules/platforms/os:android"],
    )
    target_provides_androidmk_info_test(
        name = linux_test_name,
        target_under_test = name,
        expected_static_libs = [dep_name, "libgtest_main", "libgtest"],
        expected_whole_static_libs = [whole_archive_dep_name],
        expected_shared_libs = [dynamic_dep_name, "libc++"],
        target_compatible_with = ["//build/bazel_common_rules/platforms/os:linux"],
    )
    return [
        android_test_name,
        linux_test_name,
    ]

def cc_test_test_suite(name):
    native.test_suite(
        name = name,
        tests = _cc_test_provides_androidmk_info(),
    )
