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

load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts")
load(":cc_xsd_config_library.bzl", "cc_xsd_config_library")

def _xsd_config_generates_cpp_outputs_test_impl(ctx):
    env = analysistest.begin(ctx)

    actions = analysistest.target_actions(env)

    asserts.equals(
        env,
        1,
        len(actions),
        "Incorrect number of actions",
    )

    action_outputs = actions[0].outputs.to_list()

    asserts.equals(
        env,
        len(ctx.attr.expected_output_filenames),
        len(action_outputs),
        "Incorrect number of action outputs",
    )

    for i in range(0, len(ctx.attr.expected_output_filenames)):
        asserts.equals(
            env,
            ctx.attr.expected_output_filenames[i],
            action_outputs[i].basename,
        )

    return analysistest.end(env)

_xsd_config_generates_cpp_outputs_test = analysistest.make(
    _xsd_config_generates_cpp_outputs_test_impl,
    attrs = {
        "expected_output_filenames": attr.string_list(),
    },
)

def _xsd_config_generates_parser_and_enums_by_default():
    subject_name = "xsd_config_parser_and_enum"

    # Test the internal _cc_yacc_parser_gen created by cc_xsd_config_library
    test_subject_name = subject_name + "_gen"
    test_name = subject_name + "_test"

    cc_xsd_config_library(
        name = subject_name,
        src = "foo.xsd",
        package_name = "foo",
        tags = ["manual"],
    )
    _xsd_config_generates_cpp_outputs_test(
        name = test_name,
        target_under_test = test_subject_name,
        expected_output_filenames = ["foo.cpp", "foo_enums.cpp", "foo.h", "foo_enums.h"],
    )
    return test_name

def _xsd_config_generates_parser_only():
    subject_name = "xsd_config_parser_only"

    # Test the internal _cc_yacc_parser_gen created by cc_xsd_config_library
    test_subject_name = subject_name + "_gen"
    test_name = subject_name + "_test"

    cc_xsd_config_library(
        name = subject_name,
        src = "foo.xsd",
        package_name = "foo",
        parser_only = True,
        tags = ["manual"],
    )
    _xsd_config_generates_cpp_outputs_test(
        name = test_name,
        target_under_test = test_subject_name,
        expected_output_filenames = ["foo.cpp", "foo.h", "foo_enums.h"],
    )
    return test_name

def _xsd_config_generates_enums_only():
    subject_name = "xsd_config_enums_only"

    # Test the internal _cc_yacc_parser_gen created by cc_xsd_config_library
    test_subject_name = subject_name + "_gen"
    test_name = subject_name + "_test"

    cc_xsd_config_library(
        name = subject_name,
        src = "foo.xsd",
        package_name = "foo",
        enums_only = True,
        tags = ["manual"],
    )
    _xsd_config_generates_cpp_outputs_test(
        name = test_name,
        target_under_test = test_subject_name,
        expected_output_filenames = ["foo_enums.cpp", "foo_enums.h"],
    )
    return test_name

def cc_xsd_config_library_test_suite(name):
    native.test_suite(
        name = name,
        tests = [
            _xsd_config_generates_parser_and_enums_by_default(),
            _xsd_config_generates_parser_only(),
            _xsd_config_generates_enums_only(),
        ],
    )
