"""
Copyright (C) 2023 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
"""

load("@bazel_skylib//lib:paths.bzl", "paths")
load("@rules_testing//lib:analysis_test.bzl", "analysis_test", "test_suite")
load("@rules_testing//lib:util.bzl", "TestingAspectInfo", testing_util = "util")
load(":cc_info_subject.bzl", "cc_info_subject")
load(":cc_library_headers.bzl", "cc_library_headers")

def _cc_library_headers_test_impl(env, target):
    target_subject = env.expect.that_target(target)
    target_subject.has_provider(CcInfo)
    info_subject = target_subject.provider(CcInfo, factory = cc_info_subject)
    info_subject.includes().contains_exactly([
        "build/bazel/rules/cc",
        paths.join(target[TestingAspectInfo].bin_path, "build/bazel/rules/cc"),
        "build/bazel/rules/cc/incl",
        paths.join(target[TestingAspectInfo].bin_path, "build/bazel/rules/cc/incl"),
        "abs/includes",
        paths.join(target[TestingAspectInfo].bin_path, "abs/includes"),
    ])
    info_subject.system_includes().contains_exactly([
        "build/bazel/rules/cc/sys",
        paths.join(target[TestingAspectInfo].bin_path, "build/bazel/rules/cc/sys"),
    ])
    info_subject.headers().contains_exactly([
        "build/bazel/rules/cc/foo.h",
    ])

def test_cc_library_headers(name):
    lib_name = name + "_target"
    testing_util.helper_target(
        cc_library_headers,
        name = lib_name,
        hdrs = ["foo.h"],
        export_includes = [".", "incl"],
        export_system_includes = ["sys"],
        export_absolute_includes = ["abs/includes"],
    )
    analysis_test(name, impl = _cc_library_headers_test_impl, target = lib_name)

def cc_library_headers_test_suite(name):
    test_suite(
        name = name,
        tests = [
            test_cc_library_headers,
        ],
    )
