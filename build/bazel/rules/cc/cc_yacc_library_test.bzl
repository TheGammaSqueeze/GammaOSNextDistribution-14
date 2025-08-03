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
load(":cc_yacc_library.bzl", "cc_yacc_static_library")

def _yacc_generates_c_output_test_impl(ctx):
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

_yacc_generates_c_output_test = analysistest.make(
    _yacc_generates_c_output_test_impl,
    attrs = {
        "expected_output_filenames": attr.string_list(),
    },
)

def _yacc_generates_c_output():
    subject_name = "yacc_generates_c_output"

    # Test the internal _cc_yacc_parser_gen created by cc_yacc_static_library
    test_subject_name = subject_name + "_parser"
    test_name = subject_name + "_test"

    cc_yacc_static_library(
        name = subject_name,
        src = "foo.y",
        tags = ["manual"],
    )
    _yacc_generates_c_output_test(
        name = test_name,
        target_under_test = test_subject_name,
        expected_output_filenames = ["foo.c", "foo.h"],
    )
    return test_name

def _yacc_generates_cpp_output():
    subject_name = "yacc_generates_cpp_output"

    # Test the internal _cc_yacc_parser_gen created by cc_yacc_static_library
    test_subject_name = subject_name + "_parser"
    test_name = subject_name + "_test"

    cc_yacc_static_library(
        name = subject_name,
        src = "foo.yy",
        tags = ["manual"],
    )
    _yacc_generates_c_output_test(
        name = test_name,
        target_under_test = test_subject_name,
        expected_output_filenames = ["foo.cpp", "foo.h"],
    )
    return test_name

def _yacc_generates_implicit_header_outputs():
    subject_name = "yacc_generates_implicit_headers"

    # Test the internal _cc_yacc_parser_gen created by cc_yacc_static_library
    test_subject_name = subject_name + "_parser"
    test_name = subject_name + "_test"

    cc_yacc_static_library(
        name = subject_name,
        src = "foo.y",
        gen_location_hh = True,
        gen_position_hh = True,
        tags = ["manual"],
    )
    _yacc_generates_c_output_test(
        name = test_name,
        target_under_test = test_subject_name,
        expected_output_filenames = ["foo.c", "foo.h", "location.hh", "position.hh"],
    )
    return test_name

def cc_yacc_static_library_test_suite(name):
    native.test_suite(
        name = name,
        tests = [
            _yacc_generates_c_output(),
            _yacc_generates_cpp_output(),
            _yacc_generates_implicit_header_outputs(),
        ],
    )
