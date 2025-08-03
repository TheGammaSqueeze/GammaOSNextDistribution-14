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
load(":library.bzl", "java_library")
load(":platform_compat_config.bzl", "PlatformCompatConfigInfo", "platform_compat_config")

def _platform_compat_config_test_impl(ctx):
    env = analysistest.begin(ctx)

    actions = analysistest.target_actions(env)
    target = analysistest.target_under_test(env)

    asserts.equals(
        env,
        1,
        len(actions),
        "Incorrect number of actions",
    )

    asserts.true(
        env,
        PlatformCompatConfigInfo in target,
        "Expected PlatformCompatConfigInfo in platform_compat_config providers.",
    )

    action_outputs = actions[0].outputs.to_list()

    asserts.equals(
        env,
        2,
        len(action_outputs),
        "Incorrect number of action outputs",
    )

    asserts.equals(
        env,
        ctx.attr.expected_config_filename,
        action_outputs[0].basename,
    )

    asserts.equals(
        env,
        ctx.attr.expected_metadata_filename,
        action_outputs[1].basename,
    )

    return analysistest.end(env)

_platform_compat_config_test = analysistest.make(
    _platform_compat_config_test_impl,
    attrs = {
        "expected_config_filename": attr.string(),
        "expected_metadata_filename": attr.string(),
    },
)

def test_generates_correct_outputs():
    name = "test_generates_correct_outputs"
    target_name = name + "_target"
    src_library_name = name + "_src"

    platform_compat_config(
        name = target_name,
        src = src_library_name,
    )
    java_library(
        name = src_library_name,
        sdk_version = "current",
    )

    _platform_compat_config_test(
        name = name,
        target_under_test = target_name,
        expected_config_filename = target_name + ".xml",
        expected_metadata_filename = target_name + "_meta.xml",
    )
    return name

def platform_compat_config_test_suite(name):
    native.test_suite(
        name = name,
        tests = [
            test_generates_correct_outputs(),
        ],
    )
