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
load("//build/bazel/rules/sysprop:sysprop_library.bzl", "sysprop_library")
load("//build/bazel/rules/test_common:args.bzl", "get_arg_value")
load(
    "//build/bazel/rules/test_common:paths.bzl",
    "get_output_and_package_dir_based_path",
    "get_package_dir_based_path",
)
load(":cc_sysprop_library.bzl", "cc_gen_sysprop")

def _provides_correct_outputs_test_impl(ctx):
    env = analysistest.begin(ctx)

    target_under_test = analysistest.target_under_test(env)
    output_files = target_under_test[DefaultInfo].files.to_list()
    actual_output_strings = [
        file.short_path
        for file in output_files
    ]

    asserts.equals(
        env,
        6,
        len(output_files),
        "List of outputs incorrect length",
    )
    for name in ["foo", "bar"]:
        expected_cpp_path = get_package_dir_based_path(
            env,
            "sysprop/path/to/%s.sysprop.cpp" % (name),
        )
        asserts.true(
            env,
            expected_cpp_path in actual_output_strings,
            ("Generated cpp source file for %s.sysprop not present in " +
             "output.\n" +
             "Expected Value: %s\n" +
             "Actual output: %s") % (
                name,
                expected_cpp_path,
                actual_output_strings,
            ),
        )
        expected_header_path = get_package_dir_based_path(
            env,
            "sysprop/include/path/to/%s.sysprop.h" % (name),
        )
        asserts.true(
            env,
            expected_header_path in actual_output_strings,
            ("Generated header source file for %s.sysprop not present in " +
             "output.\n" +
             "Expected Value: %s\n" +
             "Actual output: %s") % (
                name,
                expected_header_path,
                actual_output_strings,
            ),
        )
        expected_public_header_path = get_package_dir_based_path(
            env,
            "sysprop/public/include/path/to/%s.sysprop.h" % (name),
        )
        asserts.true(
            env,
            expected_public_header_path in actual_output_strings,
            ("Generated public header source file for %s.sysprop not present " +
             "in output.\n" +
             "Expected Value: %s\n" +
             "Actual output: %s") % (
                name,
                expected_public_header_path,
                actual_output_strings,
            ),
        )

    return analysistest.end(env)

provides_correct_outputs_test = analysistest.make(
    _provides_correct_outputs_test_impl,
)

# TODO(b/240466571): This test will be notably different after implementing
#                    exported include and header selection
def _provides_correct_ccinfo_test_impl(ctx):
    env = analysistest.begin(ctx)

    target_under_test = analysistest.target_under_test(env)
    target_ccinfo = target_under_test[CcInfo]
    actual_includes = target_ccinfo.compilation_context.includes.to_list()
    actual_headers = target_ccinfo.compilation_context.headers.to_list()
    expected_package_relative_include = get_package_dir_based_path(
        env,
        "sysprop/include",
    )
    asserts.true(
        env,
        expected_package_relative_include in actual_includes,
        ("Package relative include incorrect or not found in CcInfo.\n" +
         "Expected value: %s\n" +
         "Actual output: %s") % (
            expected_package_relative_include,
            actual_includes,
        ),
    )
    expected_root_relative_include = get_output_and_package_dir_based_path(
        env,
        "sysprop/include",
    )
    asserts.true(
        env,
        expected_root_relative_include in actual_includes,
        ("Root relative include incorrect or not found in CcInfo.\n" +
         "Expected value: %s\n" +
         "Actual output: %s") % (
            expected_root_relative_include,
            actual_includes,
        ),
    )
    asserts.true(
        env,
        len(actual_includes) == 2,
        ("CcInfo includes should contain a package relative and a " +
         "root-relative path and nothing else. Actual output: %s" % (
             actual_includes,
         )),
    )
    actual_header_strings = [
        header.path
        for header in actual_headers
    ]
    for name in ["foo", "bar"]:
        asserts.true(
            env,
            get_output_and_package_dir_based_path(
                env,
                "sysprop/include/path/to/%s.sysprop.h" % (name),
            ) in actual_header_strings,
            ("Generated header file for %s.sysprop not present in CcInfo " +
             "headers. Actual output: %s") % (name, actual_header_strings),
        )
    asserts.true(
        env,
        len(actual_headers) == 2,
        ("List of generated headers in CcInfo was incorrect length. Should " +
         "be exactly two. Actual output: %s" % actual_headers),
    )

    return analysistest.end(env)

provides_correct_ccinfo_test = analysistest.make(
    _provides_correct_ccinfo_test_impl,
)

def _correct_args_test_impl(ctx):
    env = analysistest.begin(ctx)

    actions = analysistest.target_actions(env)

    asserts.equals(
        env,
        2,
        len(actions),
        "Incorrect number of actions",
    )
    names = ["foo", "bar"]
    for i in range(2):
        name = names[i]
        actual_args = actions[i].argv

        asserts.equals(
            env,
            get_output_and_package_dir_based_path(env, "sysprop/include/path/to"),
            get_arg_value(actual_args, "--header-dir"),
            "--header-dir argument incorrect or not found.\n",
        )
        asserts.equals(
            env,
            get_output_and_package_dir_based_path(env, "sysprop/public/include/path/to"),
            get_arg_value(actual_args, "--public-header-dir"),
            "--public-header-dir argument incorrect or not found.\n",
        )
        asserts.equals(
            env,
            get_output_and_package_dir_based_path(env, "sysprop/path/to"),
            get_arg_value(actual_args, "--source-dir"),
            "--source-dir argument incorrect or not found.\n",
        )
        asserts.equals(
            env,
            "path/to/%s.sysprop.h" % name,
            get_arg_value(actual_args, "--include-name"),
            "--include-name argument incorrect or not found.\n",
        )
        expected_input = get_package_dir_based_path(
            env,
            "path/to/%s.sysprop" % name,
        )
        actual_cli_string = " ".join(actual_args)
        asserts.true(
            env,
            expected_input in actual_cli_string,
            ("Input argument not found.\n" +
             "Expected Value: %s\n" +
             "Command: %s") % (expected_input, actual_cli_string),
        )

    return analysistest.end(env)

correct_args_test = analysistest.make(
    _correct_args_test_impl,
)

def _create_test_targets(name, rule_func):
    wrapper_name = name + "_wrapper"
    test_name = name + "_test"
    sysprop_library(
        name = wrapper_name,
        srcs = [
            "path/to/foo.sysprop",
            "path/to/bar.sysprop",
        ],
        tags = ["manual"],
    )
    cc_gen_sysprop(
        name = name,
        dep = ":" + wrapper_name,
        tags = ["manual"],
    )
    rule_func(
        name = test_name,
        target_under_test = name,
    )
    return test_name

def cc_gen_sysprop_test_suite(name):
    native.test_suite(
        name = name,
        tests = [
            _create_test_targets(
                "provides_correct_outputs",
                provides_correct_outputs_test,
            ),
            _create_test_targets(
                "provides_correct_ccinfo",
                provides_correct_ccinfo_test,
            ),
            _create_test_targets(
                "correct_args_test",
                correct_args_test,
            ),
        ],
    )
