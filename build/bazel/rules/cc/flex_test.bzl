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
load("//build/bazel/rules/test_common:args.bzl", "get_arg_value")
load(
    "//build/bazel/rules/test_common:paths.bzl",
    "get_output_and_package_dir_based_path",
    "get_package_dir_based_path",
)
load(":flex.bzl", "genlex")

def _single_l_file_to_c_test_impl(ctx):
    env = analysistest.begin(ctx)

    actions = analysistest.target_actions(env)

    asserts.equals(env, 1, len(actions))

    actual_list_foo = [input.path for input in actions[0].inputs.to_list()]
    expected_path_foo = get_package_dir_based_path(env, "foo.l")
    asserts.true(
        env,
        expected_path_foo in actual_list_foo,
        ("Input file %s not present or incorrect in Bazel action for " +
         "target foo. Actual list of inputs: %s") % (
            expected_path_foo,
            actual_list_foo,
        ),
    )
    expected_output = get_output_and_package_dir_based_path(env, "foo.c")
    actual_outputs = [output.path for output in actions[0].outputs.to_list()]
    asserts.true(
        env,
        expected_output in actual_outputs,
        ("Expected output %s not present or incorrect in Bazel action\n" +
         "Actual list of outputs: %s") % (
            expected_output,
            actual_outputs,
        ),
    )

    return analysistest.end(env)

single_l_file_to_c_test = analysistest.make(_single_l_file_to_c_test_impl)

def _test_single_l_file_to_c():
    name = "single_l_file_to_c"
    test_name = name + "_test"
    genlex(
        name = name,
        srcs = ["foo.l"],
        tags = ["manual"],
    )
    single_l_file_to_c_test(
        name = test_name,
        target_under_test = name,
    )
    return test_name

def _single_ll_file_to_cc_test_impl(ctx):
    env = analysistest.begin(ctx)

    actions = analysistest.target_actions(env)

    asserts.equals(env, 1, len(actions))

    actual_list_foo = [input.path for input in actions[0].inputs.to_list()]
    expected_path_foo = get_package_dir_based_path(env, "foo.ll")
    asserts.true(
        env,
        expected_path_foo in actual_list_foo,
        ("Input file %s not present or incorrect in Bazel action for " +
         "target foo. Actual list of inputs: %s") % (
            expected_path_foo,
            actual_list_foo,
        ),
    )
    expected_output = get_output_and_package_dir_based_path(env, "foo.cc")
    actual_outputs = [output.path for output in actions[0].outputs.to_list()]
    asserts.true(
        env,
        expected_output in actual_outputs,
        ("Expected output %s not present or incorrect in Bazel action\n" +
         "Actual list of outputs: %s") % (
            expected_output,
            actual_outputs,
        ),
    )

    return analysistest.end(env)

single_ll_file_to_cc_test = analysistest.make(_single_ll_file_to_cc_test_impl)

def _test_single_ll_file_to_cc():
    name = "single_ll_file_to_cc"
    test_name = name + "_test"
    genlex(
        name = name,
        srcs = ["foo.ll"],
        tags = ["manual"],
    )
    single_ll_file_to_cc_test(
        name = test_name,
        target_under_test = name,
    )
    return test_name

def _multiple_files_correct_type_test_impl(ctx):
    env = analysistest.begin(ctx)

    actions = analysistest.target_actions(env)

    asserts.equals(env, 2, len(actions))

    actual_list_foo = [input.path for input in actions[0].inputs.to_list()]
    expected_path_foo = get_package_dir_based_path(env, "foo.l")
    asserts.true(
        env,
        expected_path_foo in actual_list_foo,
        ("Input file %s not present or incorrect in Bazel action for " +
         "target foo. Actual list of inputs: %s") % (
            expected_path_foo,
            actual_list_foo,
        ),
    )
    actual_list_bar = [input.path for input in actions[1].inputs.to_list()]
    expected_path_bar = get_package_dir_based_path(env, "bar.l")
    asserts.true(
        env,
        expected_path_bar in actual_list_bar,
        ("Input file %s not present or incorrect in Bazel action for " +
         "target bar. Actual list of inputs: %s") % (
            expected_path_bar,
            actual_list_bar,
        ),
    )

    expected_output = get_output_and_package_dir_based_path(env, "foo.c")
    actual_outputs = [output.path for output in actions[0].outputs.to_list()]
    asserts.true(
        env,
        expected_output in actual_outputs,
        ("Expected output %s not present or incorrect in Bazel action" +
         "for source file foo.l\n" +
         "Actual list of outputs: %s") % (
            expected_output,
            actual_outputs,
        ),
    )
    expected_output = get_output_and_package_dir_based_path(env, "bar.c")
    actual_outputs = [output.path for output in actions[1].outputs.to_list()]
    asserts.true(
        env,
        expected_output in actual_outputs,
        ("Expected output %s not present or incorrect in Bazel action " +
         "for source file bar.l\n" +
         "Actual list of outputs: %s") % (
            expected_output,
            actual_outputs,
        ),
    )

    return analysistest.end(env)

multiple_files_correct_type_test = analysistest.make(
    _multiple_files_correct_type_test_impl,
)

def _test_multiple_files_correct_type():
    name = "multiple_files_correct_type"
    test_name = name + "_test"
    genlex(
        name = name,
        srcs = ["foo.l", "bar.l"],
        tags = ["manual"],
    )
    multiple_files_correct_type_test(
        name = test_name,
        target_under_test = name,
    )
    return test_name

def _output_arg_test_impl(ctx):
    env = analysistest.begin(ctx)

    actions = analysistest.target_actions(env)
    actual_list = actions[0].argv
    cli_string = " ".join(actions[0].argv)
    expected_value = get_output_and_package_dir_based_path(env, "foo.c")

    asserts.equals(
        env,
        expected_value,
        get_arg_value(actual_list, "-o"),
        ("Argument -o not found or had unexpected value.\n" +
         "Expected value: %s\n" +
         "Command: %s") % (
            expected_value,
            cli_string,
        ),
    )

    return analysistest.end(env)

output_arg_test = analysistest.make(_output_arg_test_impl)

def _test_output_arg():
    name = "output_arg"
    test_name = name + "_test"
    genlex(
        name = name,
        srcs = ["foo.l"],
        tags = ["manual"],
    )
    output_arg_test(
        name = test_name,
        target_under_test = name,
    )
    return test_name

def _input_arg_test_impl(ctx):
    env = analysistest.begin(ctx)

    actions = analysistest.target_actions(env)
    actual_argv = actions[0].argv
    expected_value = get_package_dir_based_path(env, "foo.l")

    asserts.true(
        env,
        expected_value in actual_argv,
        "Input file %s not present or incorrect in flex command args" %
        expected_value,
    )

    return analysistest.end(env)

input_arg_test = analysistest.make(_input_arg_test_impl)

def _test_input_arg():
    name = "input_arg"
    test_name = name + "_test"
    genlex(
        name = name,
        srcs = ["foo.l"],
        tags = ["manual"],
    )
    input_arg_test(
        name = test_name,
        target_under_test = name,
    )
    return test_name

def _lexopts_test_impl(ctx):
    env = analysistest.begin(ctx)
    actions = analysistest.target_actions(env)

    actual_argv = actions[0].argv
    asserts.true(
        env,
        "foo_opt" in actual_argv,
        ("Did not find expected lexopt foo_opt %s for target foo in test " +
         "lexopts_test") % actual_argv,
    )
    asserts.true(
        env,
        "bar_opt" in actual_argv,
        ("Did not find expected lexopt bar_opt %s for target bars in test " +
         "lexopts_test") % actual_argv,
    )

    return analysistest.end(env)

lexopts_test = analysistest.make(_lexopts_test_impl)

def _test_lexopts():
    name = "lexopts"
    test_name = name + "_test"
    genlex(
        name = name,
        srcs = ["foo_lexopts.ll"],
        lexopts = ["foo_opt", "bar_opt"],
        tags = ["manual"],
    )

    lexopts_test(
        name = test_name,
        target_under_test = name,
    )
    return test_name

# TODO(b/190006308): When fixed, l and ll sources can coexist. Remove this test.
def _l_and_ll_files_fails_test_impl(ctx):
    env = analysistest.begin(ctx)

    asserts.expect_failure(
        env,
        "srcs contains both .l and .ll files. Please use separate targets.",
    )

    return analysistest.end(env)

l_and_ll_files_fails_test = analysistest.make(
    _l_and_ll_files_fails_test_impl,
    expect_failure = True,
)

def _test_l_and_ll_files_fails():
    name = "l_and_ll_files_fails"
    test_name = name + "_test"
    genlex(
        name = name,
        srcs = ["foo_fails.l", "bar_fails.ll"],
        tags = ["manual"],
    )
    l_and_ll_files_fails_test(
        name = test_name,
        target_under_test = name,
    )
    return test_name

def flex_test_suite(name):
    native.test_suite(
        name = name,
        tests = [
            _test_single_l_file_to_c(),
            _test_single_ll_file_to_cc(),
            _test_multiple_files_correct_type(),
            _test_output_arg(),
            _test_input_arg(),
            _test_lexopts(),
            _test_l_and_ll_files_fails(),
        ],
    )
