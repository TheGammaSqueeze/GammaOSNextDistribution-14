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
load(":java_xsd_config_library.bzl", "java_xsd_config_library")

def _xsd_config_generates_java_outputs_test_impl(ctx):
    env = analysistest.begin(ctx)

    actions = analysistest.target_actions(env)

    asserts.equals(
        env,
        2,  # xsdc and soong_zip
        len(actions),
        "Incorrect number of actions",
    )

    # soong_zip is the second action
    soong_zip_action_outputs = actions[1].outputs.to_list()

    asserts.equals(
        env,
        1,
        len(soong_zip_action_outputs),
        "Incorrect number of action outputs",
    )

    asserts.equals(
        env,
        ctx.attr.expected_output_filename,
        soong_zip_action_outputs[0].basename,
    )

    return analysistest.end(env)

_xsd_config_generates_java_outputs_test = analysistest.make(
    _xsd_config_generates_java_outputs_test_impl,
    attrs = {
        "expected_output_filename": attr.string(),
    },
)

def _xsd_config_generates_java_outputs():
    subject_name = "xsd_config_generates_java_outputs"

    # Test the internal _java_xsd_codegen created by java_xsd_config_library
    test_subject_name = subject_name + "_gen"
    test_name = subject_name + "_test"

    java_xsd_config_library(
        name = subject_name,
        src = "foo.xsd",
        package_name = "foo",
        tags = ["manual"],
    )
    _xsd_config_generates_java_outputs_test(
        name = test_name,
        target_under_test = test_subject_name,
        expected_output_filename = test_subject_name + ".srcjar",
    )
    return test_name

def java_xsd_config_library_test_suite(name):
    native.test_suite(
        name = name,
        tests = [
            _xsd_config_generates_java_outputs(),
        ],
    )
