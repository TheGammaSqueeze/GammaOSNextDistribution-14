# Copyright (C) 2022 The Android Open Source Project
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
load(":cc_binary.bzl", "cc_binary")
load(":cc_library_common_test.bzl", "target_provides_androidmk_info_test")
load(":cc_library_shared.bzl", "cc_library_shared")
load(":cc_library_static.bzl", "cc_library_static")

def strip_test_assert_flags(env, strip_action, strip_flags):
    # Extract these flags from strip_action (for example):
    # build/soong/scripts/strip.sh --keep-symbols --add-gnu-debuglink -i <in> -o <out> -d <out>.d
    #                              ^^^^^^^^^^^^^^ ^^^^^^^^^^^^^^^^^^^
    flag_start_idx = 1  # starts after the strip.sh executable
    flag_end_idx = strip_action.argv.index("-i")  # end of the flags
    asserts.equals(
        env,
        strip_action.argv[flag_start_idx:flag_end_idx],
        strip_flags,
    )

def _cc_binary_strip_test(ctx):
    env = analysistest.begin(ctx)
    actions = analysistest.target_actions(env)
    filtered_actions = [a for a in actions if a.mnemonic == "CcStrip"]
    on_target = ctx.target_platform_has_constraint(
        ctx.attr._android_constraint[platform_common.ConstraintValueInfo],
    )
    if ctx.attr.strip_flags or on_target:
        # expected to find strip flags, so look for a CcStrip action.
        asserts.true(
            env,
            len(filtered_actions) == 1,
            "expected to find an action with CcStrip mnemonic in %s" % actions,
        )
        if ctx.attr.strip_flags or not on_target:
            strip_test_assert_flags(env, filtered_actions[0], ctx.attr.strip_flags)
        return analysistest.end(env)
    else:
        asserts.true(
            env,
            len(filtered_actions) == 0,
            "expected to not find an action with CcStrip mnemonic in %s" % actions,
        )
        return analysistest.end(env)

cc_binary_strip_test = analysistest.make(
    _cc_binary_strip_test,
    attrs = {
        "strip_flags": attr.string_list(),
        "_android_constraint": attr.label(default = Label("//build/bazel_common_rules/platforms/os:android")),
    },
)

def _cc_binary_strip_default():
    name = "cc_binary_strip_default"
    test_name = name + "_test"

    cc_binary(
        name = name,
        srcs = ["main.cc"],
        tags = ["manual"],
    )

    cc_binary_strip_test(
        name = test_name,
        target_under_test = name,
        strip_flags = [],
    )

    return test_name

def _cc_binary_strip_keep_symbols():
    name = "cc_binary_strip_keep_symbols"
    test_name = name + "_test"

    cc_binary(
        name = name,
        srcs = ["main.cc"],
        tags = ["manual"],
        strip = {"keep_symbols": True},
    )

    cc_binary_strip_test(
        name = test_name,
        target_under_test = name,
        strip_flags = [
            "--keep-symbols",
            "--add-gnu-debuglink",
        ],
    )

    return test_name

def _cc_binary_strip_keep_symbols_and_debug_frame():
    name = "cc_binary_strip_keep_symbols_and_debug_frame"
    test_name = name + "_test"

    cc_binary(
        name = name,
        srcs = ["main.cc"],
        tags = ["manual"],
        strip = {"keep_symbols_and_debug_frame": True},
    )

    cc_binary_strip_test(
        name = test_name,
        target_under_test = name,
        strip_flags = [
            "--keep-symbols-and-debug-frame",
            "--add-gnu-debuglink",
        ],
    )

    return test_name

def _cc_binary_strip_keep_symbols_list():
    name = "cc_binary_strip_keep_symbols_list"
    test_name = name + "_test"

    cc_binary(
        name = name,
        srcs = ["main.cc"],
        tags = ["manual"],
        strip = {"keep_symbols_list": ["foo", "bar"]},
    )

    cc_binary_strip_test(
        name = test_name,
        target_under_test = name,
        strip_flags = [
            "-kfoo,bar",
            "--add-gnu-debuglink",
        ],
    )

    return test_name

def _cc_binary_strip_all():
    name = "cc_binary_strip_all"
    test_name = name + "_test"

    cc_binary(
        name = name,
        srcs = ["main.cc"],
        tags = ["manual"],
        strip = {"all": True},
    )

    cc_binary_strip_test(
        name = test_name,
        target_under_test = name,
        strip_flags = [
            "--add-gnu-debuglink",
        ],
    )

    return test_name

def _cc_binary_suffix_test_impl(ctx):
    env = analysistest.begin(ctx)
    target = analysistest.target_under_test(env)
    info = target[DefaultInfo]
    suffix = ctx.attr.suffix

    outputs = info.files.to_list()
    asserts.true(
        env,
        len(outputs) == 1,
        "Expected 1 output file; got %s" % outputs,
    )
    out = outputs[0]
    asserts.true(
        env,
        out.path.endswith(suffix),
        "Expected output filename to end in `%s`; it was instead %s" % (suffix, out),
    )

    if ctx.attr.stem:
        asserts.equals(
            env,
            out.basename,
            ctx.attr.stem,
            "Expected output filename %s to be equal to `stem` attribute %s" % (out, ctx.attr.stem),
        )

    return analysistest.end(env)

cc_binary_suffix_test = analysistest.make(
    _cc_binary_suffix_test_impl,
    attrs = {
        "stem": attr.string(),
        "suffix": attr.string(),
    },
)

def _cc_binary_suffix():
    name = "cc_binary_suffix"
    test_name = name + "_test"
    suffix = "-suf"

    cc_binary(
        name,
        srcs = ["src.cc"],
        tags = ["manual"],
        suffix = suffix,
    )
    cc_binary_suffix_test(
        name = test_name,
        target_under_test = name,
        suffix = suffix,
    )
    return test_name

def _cc_binary_empty_suffix():
    name = "cc_binary_empty_suffix"
    test_name = name + "_test"

    cc_binary(
        name,
        srcs = ["src.cc"],
        tags = ["manual"],
    )
    cc_binary_suffix_test(
        name = test_name,
        target_under_test = name,
    )
    return test_name

def _cc_binary_with_stem():
    name = "cc_binary_with_stem"
    test_name = name + "_test"

    cc_binary(
        name,
        srcs = ["src.cc"],
        stem = "bar",
        tags = ["manual"],
    )
    cc_binary_suffix_test(
        name = test_name,
        stem = "bar",
        target_under_test = name,
    )
    return test_name

def _cc_binary_provides_androidmk_info():
    name = "cc_binary_provides_androidmk_info"
    dep_name = name + "_static_dep"
    whole_archive_dep_name = name + "_whole_archive_dep"
    dynamic_dep_name = name + "_dynamic_dep"
    test_name = name + "_test"

    cc_library_static(
        name = dep_name,
        srcs = ["foo.c"],
        tags = ["manual"],
    )
    cc_library_static(
        name = whole_archive_dep_name,
        srcs = ["foo.c"],
        tags = ["manual"],
    )
    cc_library_shared(
        name = dynamic_dep_name,
        srcs = ["foo.c"],
        tags = ["manual"],
    )
    cc_binary(
        name = name,
        srcs = ["foo.cc"],
        deps = [dep_name],
        whole_archive_deps = [whole_archive_dep_name],
        dynamic_deps = [dynamic_dep_name],
        tags = ["manual"],
    )
    android_test_name = test_name + "_android"
    linux_test_name = test_name + "_linux"
    target_provides_androidmk_info_test(
        name = android_test_name,
        target_under_test = name,
        expected_static_libs = [dep_name, "libc++demangle", "libunwind"],
        expected_whole_static_libs = [whole_archive_dep_name],
        expected_shared_libs = [dynamic_dep_name, "libc++", "libc_stub_libs-current", "libdl_stub_libs-current", "libm_stub_libs-current"],
        target_compatible_with = ["//build/bazel_common_rules/platforms/os:android"],
    )
    target_provides_androidmk_info_test(
        name = linux_test_name,
        target_under_test = name,
        expected_static_libs = [dep_name],
        expected_whole_static_libs = [whole_archive_dep_name],
        expected_shared_libs = [dynamic_dep_name, "libc++"],
        target_compatible_with = ["//build/bazel_common_rules/platforms/os:linux"],
    )
    return [
        android_test_name,
        linux_test_name,
    ]

def _cc_bad_linkopts_test_impl(ctx):
    env = analysistest.begin(ctx)
    if ctx.target_platform_has_constraint(ctx.attr._android_constraint[platform_common.ConstraintValueInfo]):
        asserts.expect_failure(env, "Library requested via -l is not supported for device builds. Use implementation_deps instead.")
    else:
        asserts.expect_failure(env, "Host library(s) requested via -l is not available in the toolchain.")
    return analysistest.end(env)

cc_bad_linkopts_test = analysistest.make(
    _cc_bad_linkopts_test_impl,
    expect_failure = True,
    attrs = {
        "_android_constraint": attr.label(
            default = Label("//build/bazel_common_rules/platforms/os:android"),
        ),
    },
)

# Test that an error is raised if a user requests a library that is not available in the toolchain.
def _cc_binary_bad_linkopts():
    subject_name = "cc_binary_bad_linkopts"
    test_name = subject_name + "_test"

    cc_binary(
        name = subject_name,
        linkopts = ["-lunknown"],
        tags = ["manual"],
    )
    cc_bad_linkopts_test(
        name = test_name,
        target_under_test = subject_name,
    )
    return test_name

def cc_binary_test_suite(name):
    native.test_suite(
        name = name,
        tests = [
            _cc_binary_strip_default(),
            _cc_binary_strip_keep_symbols(),
            _cc_binary_strip_keep_symbols_and_debug_frame(),
            _cc_binary_strip_keep_symbols_list(),
            _cc_binary_strip_all(),
            _cc_binary_suffix(),
            _cc_binary_empty_suffix(),
            _cc_binary_with_stem(),
            _cc_binary_bad_linkopts(),
        ] + _cc_binary_provides_androidmk_info(),
    )
