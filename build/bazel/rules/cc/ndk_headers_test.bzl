"""Copyright (C) 2023 The Android Open Source Project

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
load("@bazel_skylib//lib:sets.bzl", "sets")
load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts")
load(":ndk_headers.bzl", "ndk_headers")

def _ndk_headers_test_impl(ctx):
    env = analysistest.begin(ctx)
    target_under_test = analysistest.target_under_test(env)
    target_bin_dir_path = analysistest.target_bin_dir_path(env)

    # check that versioner was run for versioned NDK headers
    if ctx.attr.expected_run_versioner:
        version_action = [a for a in analysistest.target_actions(env) if a.mnemonic == "VersionBionicHeaders"]
        asserts.equals(
            env,
            len(version_action),
            1,
            "Expected versioner to run once",
        )

    asserts.set_equals(
        env,
        expected = sets.make([
            paths.join(ctx.attr.expected_isystem, file)
            for file in ctx.attr.expected_hdrs
        ]),
        actual = sets.make([
            file.short_path
            for file in target_under_test[DefaultInfo].files.to_list()
        ]),
    )

    compilation_context = target_under_test[CcInfo].compilation_context

    # check -I
    asserts.equals(
        env,
        [],
        compilation_context.includes.to_list(),
        "ndk headers should be added as -isystem and not -I",
    )

    # check -isystem
    asserts.equals(
        env,
        [
            paths.join(
                target_bin_dir_path,
                ctx.attr.expected_isystem,
            ),
            # check for the NDK triple
            paths.join(
                target_bin_dir_path,
                ctx.attr.expected_isystem,
                "arm-linux-androideabi",
            ),
        ],
        compilation_context.system_includes.to_list(),
        "CcInfo returned by ndk headers does not have the correct -isystem",
    )

    return analysistest.end(env)

ndk_headers_test = analysistest.make(
    _ndk_headers_test_impl,
    attrs = {
        "expected_hdrs": attr.string_list(),
        "expected_isystem": attr.string(doc = "expected dir relative to bin dir that will be provided as -isystem to rdeps"),
        "expected_run_versioner": attr.bool(default = False),
    },
    # Pin the test to a consistent arch
    config_settings = {
        "//command_line_option:platforms": "@//build/bazel/tests/products:aosp_arm_for_testing",
    },
)

def _test_ndk_headers_simple():
    test_name = "ndk_headers_simple"
    target_under_test_name = test_name + "_target"

    ndk_headers(
        name = target_under_test_name,
        hdrs = ["a/aa.h", "a/ab.h"],
        tags = ["manual"],
    )

    ndk_headers_test(
        name = test_name,
        target_under_test = target_under_test_name,
        expected_hdrs = ["a/aa.h", "a/ab.h"],
        expected_isystem = "build/bazel/rules/cc/" + target_under_test_name,
    )

    return test_name

def _test_ndk_headers_non_empty_strip_import():
    test_name = "ndk_headers_non_empty_strip_import"
    target_under_test_name = test_name + "_target"

    ndk_headers(
        name = target_under_test_name,
        strip_import_prefix = "a",
        hdrs = ["a/aa.h", "a/ab.h"],
        tags = ["manual"],
    )

    ndk_headers_test(
        name = test_name,
        target_under_test = target_under_test_name,
        expected_hdrs = ["aa.h", "ab.h"],
        expected_isystem = "build/bazel/rules/cc/" + target_under_test_name,
    )

    return test_name

def _test_ndk_headers_non_empty_import():
    test_name = "ndk_headers_non_empty_import"
    target_under_test_name = test_name + "_target"

    ndk_headers(
        name = target_under_test_name,
        import_prefix = "b",
        hdrs = ["a/aa.h", "a/ab.h"],
        tags = ["manual"],
    )

    ndk_headers_test(
        name = test_name,
        target_under_test = target_under_test_name,
        expected_hdrs = ["b/a/aa.h", "b/a/ab.h"],
        expected_isystem = "build/bazel/rules/cc/" + target_under_test_name,
    )

    return test_name

def _test_ndk_headers_non_empty_strip_import_and_import():
    test_name = "ndk_headers_non_empty_strip_import_and_import"
    target_under_test_name = test_name + "_target"

    ndk_headers(
        name = target_under_test_name,
        strip_import_prefix = "a",
        import_prefix = "b",
        hdrs = ["a/aa.h", "a/ab.h"],
        tags = ["manual"],
    )

    ndk_headers_test(
        name = test_name,
        target_under_test = target_under_test_name,
        expected_hdrs = ["b/aa.h", "b/ab.h"],
        expected_isystem = "build/bazel/rules/cc/" + target_under_test_name,
    )

    return test_name

def _test_versioned_ndk_headers_non_empty_strip_import_and_import():
    test_name = "versioned_ndk_headers_non_empty_strip_import_and_import"
    target_under_test_name = test_name + "_target"

    ndk_headers(
        name = target_under_test_name,
        strip_import_prefix = "a",
        import_prefix = "b",
        hdrs = ["a/aa.h", "a/ab.h"],
        run_versioner = True,
        tags = ["manual"],
    )

    ndk_headers_test(
        name = test_name,
        target_under_test = target_under_test_name,
        expected_hdrs = ["b/aa.h", "b/ab.h"],
        expected_isystem = "build/bazel/rules/cc/" + target_under_test_name + ".versioned",
        expected_run_versioner = True,
    )

    return test_name

def ndk_headers_test_suite(name):
    native.test_suite(
        name = name,
        tests = [
            _test_ndk_headers_simple(),
            _test_ndk_headers_non_empty_strip_import(),
            _test_ndk_headers_non_empty_import(),
            _test_ndk_headers_non_empty_strip_import_and_import(),
            _test_versioned_ndk_headers_non_empty_strip_import_and_import(),
        ],
    )
