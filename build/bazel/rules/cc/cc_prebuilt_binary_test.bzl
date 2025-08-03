"""Copyright (C) 2022 The Android Open Source Project

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

load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts")
load(":cc_binary_test.bzl", "strip_test_assert_flags")
load(":cc_prebuilt_binary.bzl", "cc_prebuilt_binary")

def _cc_prebuilt_binary_basic_test_impl(ctx):
    env = analysistest.begin(ctx)
    target = analysistest.target_under_test(env)
    outs = target[DefaultInfo].files.to_list()
    asserts.true(
        env,
        len(outs) == 1,
        "expected there to be 1 output but got:\n" + str(outs),
    )
    return analysistest.end(env)

_cc_prebuilt_binary_basic_test = analysistest.make(_cc_prebuilt_binary_basic_test_impl)

def _cc_prebuilt_binary_simple_test():
    name = "cc_prebuilt_binary_simple"
    cc_prebuilt_binary(
        name = name,
        src = "bin",
        tags = ["manual"],
    )
    test_name = name + "_test"
    _cc_prebuilt_binary_basic_test(
        name = test_name,
        target_under_test = name,
    )
    return test_name

def _cc_prebuilt_binary_stripping_flags_test_impl(ctx):
    env = analysistest.begin(ctx)
    actions = analysistest.target_actions(env)
    strip_acts = [a for a in actions if a.mnemonic == "CcStrip"]
    has_strip = len(strip_acts) > 0
    asserts.true(
        env,
        has_strip,
        "expected to find an action with CcStrip mnemonic in:\n%s" % actions,
    )
    if has_strip:
        strip_test_assert_flags(env, strip_acts[0], ctx.attr.strip_flags)
    return analysistest.end(env)

__cc_prebuilt_binary_stripping_flags_test = analysistest.make(
    _cc_prebuilt_binary_stripping_flags_test_impl,
    attrs = dict(
        strip_flags = attr.string_list(),
    ),
)

def _cc_prebuilt_binary_stripping_flags_test(**kwargs):
    __cc_prebuilt_binary_stripping_flags_test(
        target_compatible_with = ["//build/bazel_common_rules/platforms/os:android"],
        **kwargs
    )

def _cc_prebuilt_binary_strip_keep_symbols_test():
    name = "cc_prebuilt_binary_strip_keep_symbols"
    cc_prebuilt_binary(
        name = name,
        src = "bin",
        keep_symbols = True,
        tags = ["manual"],
    )
    test_name = name + "_test"
    _cc_prebuilt_binary_stripping_flags_test(
        name = test_name,
        target_under_test = name,
        strip_flags = [
            "--keep-symbols",
            "--add-gnu-debuglink",
        ],
    )
    return test_name

def _cc_prebuilt_binary_strip_keep_symbols_and_debug_frame_test():
    name = "cc_prebuilt_binary_strip_keep_symbols_and_debug_frame"
    cc_prebuilt_binary(
        name = name,
        src = "bin",
        keep_symbols_and_debug_frame = True,
        tags = ["manual"],
    )
    test_name = name + "_test"
    _cc_prebuilt_binary_stripping_flags_test(
        name = test_name,
        target_under_test = name,
        strip_flags = [
            "--keep-symbols-and-debug-frame",
            "--add-gnu-debuglink",
        ],
    )
    return test_name

def _cc_prebuilt_binary_strip_keep_symbols_list_test():
    name = "cc_prebuilt_binary_strip_keep_symbols_list"
    symbols = ["foo", "bar", "baz"]
    cc_prebuilt_binary(
        name = name,
        src = "bin",
        keep_symbols_list = symbols,
        tags = ["manual"],
    )
    test_name = name + "_test"
    _cc_prebuilt_binary_stripping_flags_test(
        name = test_name,
        target_under_test = name,
        strip_flags = [
            "-k" + ",".join(symbols),
            "--add-gnu-debuglink",
        ],
    )
    return test_name

def _cc_prebuilt_binary_strip_all_test():
    name = "cc_prebuilt_binary_strip_all"
    cc_prebuilt_binary(
        name = name,
        src = "bin",
        all = True,
        tags = ["manual"],
    )
    test_name = name + "_test"
    _cc_prebuilt_binary_stripping_flags_test(
        name = test_name,
        target_under_test = name,
        strip_flags = [
            "--add-gnu-debuglink",
        ],
    )
    return test_name

def _cc_prebuilt_binary_no_stripping_action_test_impl(ctx):
    env = analysistest.begin(ctx)
    actions = analysistest.target_actions(env)
    mnemonics = [a.mnemonic for a in actions]
    has_strip = "CcStrip" in mnemonics
    asserts.false(
        env,
        has_strip,
        "expected no action with CcStrip mnemonic in:\n%s" % actions,
    )
    return analysistest.end(env)

__cc_prebuilt_binary_no_stripping_action_test = analysistest.make(
    _cc_prebuilt_binary_no_stripping_action_test_impl,
)

def _cc_prebuilt_binary_no_stripping_action_test(**kwargs):
    __cc_prebuilt_binary_no_stripping_action_test(
        target_compatible_with = ["//build/bazel_common_rules/platforms/os:android"],
        **kwargs
    )

def _cc_prebuilt_binary_strip_none_test():
    name = "cc_prebuilt_binary_strip_none"
    cc_prebuilt_binary(
        name = name,
        src = "bin",
        none = True,
        tags = ["manual"],
    )
    test_name = name + "_test"
    _cc_prebuilt_binary_no_stripping_action_test(
        name = test_name,
        target_under_test = name,
    )
    return test_name

def _cc_prebuilt_binary_host_test(**kwargs):
    __cc_prebuilt_binary_no_stripping_action_test(
        target_compatible_with = select({
            "//build/bazel_common_rules/platforms/os:android": ["@platforms//:incompatible"],
            "//conditions:default": [],
        }),
        **kwargs
    )

def _cc_prebuilt_binary_no_strip_host_test():
    name = "cc_prebuilt_binary_no_strip_host"
    cc_prebuilt_binary(
        name = name,
        src = "bin",
        tags = ["manual"],
    )
    test_name = name + "_test"
    _cc_prebuilt_binary_host_test(
        name = test_name,
        target_under_test = name,
    )
    return test_name

def cc_prebuilt_binary_test_suite(name):
    native.test_suite(
        name = name,
        tests = [
            _cc_prebuilt_binary_simple_test(),
            _cc_prebuilt_binary_strip_none_test(),
            _cc_prebuilt_binary_strip_keep_symbols_test(),
            _cc_prebuilt_binary_strip_keep_symbols_and_debug_frame_test(),
            _cc_prebuilt_binary_strip_keep_symbols_list_test(),
            _cc_prebuilt_binary_strip_all_test(),
            _cc_prebuilt_binary_no_strip_host_test(),
        ],
    )
