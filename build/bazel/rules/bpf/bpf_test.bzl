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
load(":bpf.bzl", "bpf")

def _basic_bpf_test_impl(ctx):
    env = analysistest.begin(ctx)
    actions = analysistest.target_actions(env)
    bpf_target = analysistest.target_under_test(env)

    if len(ctx.attr.expected_flags) > 0:
        for flag in ctx.attr.expected_flags:
            asserts.true(
                env,
                flag in actions[0].argv,
                "Expected flag (%s) is not in actual flags" % (flag),
            )

    if len(ctx.attr.unexpected_flags) > 0:
        for flag in ctx.attr.unexpected_flags:
            asserts.true(
                env,
                flag not in actions[0].argv,
                "Unexpected flag (%s) is in actual flags" % (flag),
            )

    if len(ctx.attr.includes) > 0:
        for dir in ctx.attr.includes:
            index = actions[0].argv.index(dir)
            asserts.true(
                env,
                actions[0].argv[index - 1] == "-I",
                "Directory %s is not after '-I' tag in clang command" % (dir),
            )

    asserts.equals(
        env,
        expected = 2 if ctx.attr.expect_strip else 1,
        actual = len(actions),
    )

    if ctx.attr.expect_strip:
        asserts.true(
            env,
            actions[-1].argv[0].endswith("llvm-strip"),
            "No strip action is executed when btf is True",
        )

    asserts.true(
        env,
        "unstripped" not in bpf_target[DefaultInfo].files.to_list()[0].path,
        "'unstripped' is in the output file path",
    )

    return analysistest.end(env)

basic_bpf_test = analysistest.make(
    _basic_bpf_test_impl,
    attrs = {
        "expected_flags": attr.string_list(),
        "unexpected_flags": attr.string_list(),
        "includes": attr.string_list(),
        "expect_strip": attr.bool(),
    },
)

def bpf_fail_test_impl(ctx):
    env = analysistest.begin(ctx)

    asserts.expect_failure(
        env,
        "Invalid character '_' in source name",
    )

    return analysistest.end(env)

bpf_fail_test = analysistest.make(
    bpf_fail_test_impl,
    expect_failure = True,
)

def test_all_attrs_btf_true():
    name = "all_attrs_btf_true_test"
    copts = ["cflag1", "cflag2"]
    absolute_includes = ["foo/bar1", "foo/bar2"]
    bpf(
        name = name + "_target",
        srcs = ["testAllAttrsBtfTrueSrc.c"],
        copts = copts,
        absolute_includes = absolute_includes,
        btf = True,
        tags = ["manual"],
    )
    basic_bpf_test(
        name = name,
        target_under_test = name + "_target",
        expected_flags = ["-g"] + copts,
        includes = absolute_includes,
        expect_strip = True,
    )
    return name

def test_btf_false():
    name = "btf_false_test"
    bpf(
        name = name + "_target",
        srcs = ["testBtfFalse.c"],
        copts = ["copts1", "copts2"],
        absolute_includes = ["foo/bar1", "foo/bar2"],
        btf = False,
        tags = ["manual"],
    )
    basic_bpf_test(
        name = name,
        target_under_test = name + "_target",
        unexpected_flags = ["-g"],
        expect_strip = False,
    )
    return name

def test_invalid_src_name():
    name = "invalid_src_name_test"
    bpf(
        name = name + "_target",
        srcs = [name + "_src.c"],
        copts = ["copts1", "copts2"],
        absolute_includes = ["foo/bar1", "foo/bar2"],
        btf = True,
        tags = ["manual"],
    )
    bpf_fail_test(
        name = name,
        target_under_test = name + "_target",
    )
    return name

def bpf_test_suite(name):
    native.test_suite(
        name = name,
        tests = [
            test_all_attrs_btf_true(),
            test_btf_false(),
            test_invalid_src_name(),
        ],
    )
