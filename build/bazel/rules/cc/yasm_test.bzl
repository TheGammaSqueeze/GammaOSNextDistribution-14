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

load("@bazel_skylib//lib:paths.bzl", "paths")
load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts")
load(":yasm.bzl", "yasm")

def _basic_yasm_test_impl(ctx):
    env = analysistest.begin(ctx)
    actions = analysistest.target_actions(env)

    for action in actions:
        asserts.equals(
            env,
            action.mnemonic,
            "yasm",
        )
        src = action.argv[-1]
        asserts.equals(
            env,
            action.argv[-3],
            "-o",
        )
        asserts.true(
            env,
            action.argv[-2].endswith(paths.replace_extension(src, ".o")),
            "-o argument is expected to end with the src file as a .o",
        )
        asserts.true(
            env,
            " ".join(ctx.attr.expected_flags) in " ".join(action.argv),
            "Expected flags (%s) were not in actual flags (%s)" % (ctx.attr.expected_flags, action.argv),
        )

    return analysistest.end(env)

basic_yasm_test = analysistest.make(
    _basic_yasm_test_impl,
    attrs = {
        "expected_flags": attr.string_list(
            doc = "Flags expected to be on the command line.",
        ),
    },
)

def test_single_file():
    name = "test_single_file"
    yasm(
        name = name + "_target",
        srcs = [name + "_file.asm"],
        tags = ["manual"],
    )
    basic_yasm_test(
        name = name,
        target_under_test = name + "_target",
    )
    return name

def test_multiple_files():
    name = "test_multiple_files"
    yasm(
        name = name + "_target",
        srcs = [
            name + "_file1.asm",
            name + "_file2.asm",
        ],
        tags = ["manual"],
    )
    basic_yasm_test(
        name = name,
        target_under_test = name + "_target",
    )
    return name

def test_custom_flags():
    name = "test_custom_flags"
    yasm(
        name = name + "_target",
        srcs = [name + "_file.asm"],
        flags = ["-DNEON_INTRINSICS", "-mfpu=neon"],
        tags = ["manual"],
    )
    basic_yasm_test(
        name = name,
        target_under_test = name + "_target",
        expected_flags = ["-DNEON_INTRINSICS", "-mfpu=neon"],
    )
    return name

def test_include_dirs():
    name = "test_include_dirs"
    yasm(
        name = name + "_target",
        srcs = [name + "_file.asm"],
        include_dirs = ["foo/bar"],
        tags = ["manual"],
    )
    basic_yasm_test(
        name = name,
        target_under_test = name + "_target",
        expected_flags = ["-Ibuild/bazel/rules/cc/foo/bar"],
    )
    return name

def yasm_test_suite(name):
    native.test_suite(
        name = name,
        tests = [
            test_single_file(),
            test_multiple_files(),
            test_custom_flags(),
            test_include_dirs(),
        ],
    )
