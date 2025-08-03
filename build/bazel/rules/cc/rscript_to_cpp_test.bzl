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

load("@bazel_skylib//lib:new_sets.bzl", "sets")
load("@bazel_skylib//lib:paths.bzl", "paths")
load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts")
load("@soong_injection//cc_toolchain:config_constants.bzl", "constants")
load(
    "//build/bazel/rules/test_common:args.bzl",
    "get_arg_value",
    "get_arg_values",
)
load(":rscript_to_cpp.bzl", "rscript_to_cpp")

SRCS = ["foo.rscript", "bar.fs"]
USER_FLAGS = ["userFlag1"]

def _test_rscript_to_cpp_commands_impl(ctx):
    env = analysistest.begin(ctx)
    actions = analysistest.target_actions(env)

    args = actions[0].argv
    asserts.true(env, len(args) >= 6)
    expected_output_path = paths.join(
        analysistest.target_bin_dir_path(env),
        ctx.label.package,
    )
    asserts.equals(
        env,
        get_arg_value(args, "-o"),
        expected_output_path,
        "Argument -o not found or had unexpected value. Expected {} got {}"
            .format(expected_output_path, get_arg_value(args, "-o")),
    )

    asserts.true(
        env,
        "-reflect-c++" in args,
        "Argument -reflect-c++ not found",
    )

    asserts.true(
        env,
        "-Wall" in args,
        "Argument -Wall not found",
    )

    asserts.true(
        env,
        "-Werror" in args,
        "Argument -Werror not found",
    )

    for flag in USER_FLAGS:
        asserts.true(
            env,
            flag in args,
            "Expected user defined flag {} not found".format(flag),
        )

    includeFlags = get_arg_values(args, "-I")
    asserts.set_equals(
        env,
        sets.make(constants.RsGlobalIncludes),
        sets.make(includeFlags),
        "Incorrect include statements",
    )

    asserts.true(
        env,
        paths.join(ctx.label.package, "baz.rscript"),
        "Expected argument baz.rscript not found",
    )

    return analysistest.end(env)

rscript_to_cpp_commands_test = analysistest.make(
    _test_rscript_to_cpp_commands_impl,
)

def _test_rscript_to_cpp_commands():
    name = "rscript_to_cpp_commands"
    test_name = name + "_test"
    rscript_to_cpp(
        name = name,
        srcs = ["baz.rscript"],
        flags = USER_FLAGS,
        tags = ["manual"],
    )

    rscript_to_cpp_commands_test(
        name = test_name,
        target_under_test = name,
    )

    return [test_name]

def _test_rscript_to_cpp_config_impl(ctx):
    env = analysistest.begin(ctx)
    actions = analysistest.target_actions(env)

    args = actions[0].argv

    asserts.true(
        env,
        ctx.attr._expectedConfigFlag in args,
        "Expected configuration {} not found"
            .format(ctx.attr._expectedConfigFlag),
    )

    return analysistest.end(env)

rscript_to_cpp_config_x86_test = analysistest.make(
    _test_rscript_to_cpp_config_impl,
    attrs = {
        "_expectedConfigFlag": attr.string(default = "-m32"),
    },
    config_settings = {
        "//command_line_option:platforms": "@//build/bazel/tests/products:aosp_x86_for_testing",
    },
)

rscript_to_cpp_config_x86_64_test = analysistest.make(
    _test_rscript_to_cpp_config_impl,
    attrs = {
        "_expectedConfigFlag": attr.string(default = "-m64"),
    },
    config_settings = {
        "//command_line_option:platforms": "@//build/bazel/tests/products:aosp_x86_64_for_testing",
    },
)

rscript_to_cpp_config_arm_test = analysistest.make(
    _test_rscript_to_cpp_config_impl,
    attrs = {
        "_expectedConfigFlag": attr.string(default = "-m32"),
    },
    config_settings = {
        "//command_line_option:platforms": "@//build/bazel/tests/products:aosp_arm_for_testing",
    },
)

rscript_to_cpp_config_arm64_test = analysistest.make(
    _test_rscript_to_cpp_config_impl,
    attrs = {
        "_expectedConfigFlag": attr.string(default = "-m64"),
    },
    config_settings = {
        "//command_line_option:platforms": "@//build/bazel/tests/products:aosp_arm64_for_testing",
    },
)

def _test_rscript_to_cpp_config():
    name = "rscript_to_cpp_config"
    x86_test_name = name + "_x86_test"
    x86_64_test_name = name + "_x86_64_test"
    arm_test_name = name + "_arm_test"
    arm64_test_name = name + "_arm64_test"

    rscript_to_cpp(
        name = name,
        srcs = SRCS,
        tags = ["manual"],
    )

    rscript_to_cpp_config_x86_test(
        name = x86_test_name,
        target_under_test = name,
    )

    rscript_to_cpp_config_x86_64_test(
        name = x86_64_test_name,
        target_under_test = name,
    )

    rscript_to_cpp_config_arm_test(
        name = arm_test_name,
        target_under_test = name,
    )

    rscript_to_cpp_config_arm64_test(
        name = arm64_test_name,
        target_under_test = name,
    )
    return [x86_test_name, x86_64_test_name, arm_test_name, arm64_test_name]

def _test_rscript_to_cpp_inputs_impl(ctx):
    env = analysistest.begin(ctx)
    actions = analysistest.target_actions(env)
    asserts.equals(
        env,
        1,
        len(actions),
        "expected  1 action got {}".format(actions),
    )

    in_files = [x.basename for x in actions[0].inputs.to_list()]

    for f in in_files:
        asserts.true(
            env,
            f in SRCS or paths.split_extension(f)[1] in [".rsh", ".h"] or
            f == "llvm-rs-cc",
            "Expected inputs to be .rsh/.h header files, llvm-rs-cc," +
            "or in srcs {} but got {}".format(SRCS, f),
        )

    return analysistest.end(env)

rscript_to_cpp_inputs_test = analysistest.make(_test_rscript_to_cpp_inputs_impl)

def _test_rscript_to_cpp_inputs():
    name = "rscript_to_cpp_inputs"
    test_name = name + "_test"

    rscript_to_cpp(
        name = name,
        srcs = SRCS,
        tags = ["manual"],
    )

    rscript_to_cpp_inputs_test(
        name = test_name,
        target_under_test = name,
    )
    return [test_name]

def _test_rscript_to_cpp_outputs_impl(ctx):
    env = analysistest.begin(ctx)
    actions = analysistest.target_actions(env)
    out_files = [x.basename for x in actions[0].outputs.to_list()]
    expected_outs = []
    for f in SRCS:
        expected_outs.append("ScriptC_" + paths.replace_extension(f, ".cpp"))
        expected_outs.append("ScriptC_" + paths.replace_extension(f, ".h"))

    asserts.set_equals(
        env,
        sets.make(expected_outs),
        sets.make(out_files),
        "Output: Expected {} but got {}".format(expected_outs, out_files),
    )

    target_under_test = analysistest.target_under_test(env)
    info = target_under_test[DefaultInfo]
    info_output = [x.basename for x in info.files.to_list()]
    asserts.set_equals(
        env,
        sets.make(expected_outs),
        sets.make(info_output),
        "expected output filename to be {} but got {}".format(
            expected_outs,
            info_output,
        ),
    )

    return analysistest.end(env)

rscript_to_cpp_outputs_test = analysistest.make(_test_rscript_to_cpp_outputs_impl)

def _test_rscript_to_cpp_outputs():
    name = "rscript_to_cpp_outputs"
    test_name = name + "_test"

    rscript_to_cpp(
        name = name,
        srcs = SRCS,
        tags = ["manual"],
    )

    rscript_to_cpp_outputs_test(
        name = test_name,
        target_under_test = name,
    )
    return [test_name]

def rscript_to_cpp_test_suite(name):
    native.test_suite(
        name = name,
        tests =
            _test_rscript_to_cpp_commands() +
            _test_rscript_to_cpp_config() +
            _test_rscript_to_cpp_inputs() +
            _test_rscript_to_cpp_outputs(),
    )
