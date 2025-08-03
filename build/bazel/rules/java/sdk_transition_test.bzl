# Copyright (C) 2023 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

load(":sdk_transition.bzl", "sdk_transition")
load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts")
load("@bazel_skylib//rules:common_settings.bzl", "BuildSettingInfo")

SdkConfig = provider(
    "Info about the config settings of the leaf dependency (in a linear dependency chain only)",
    fields = {
        "java_version": "the value of the //build/bazel/rules/java:version setting.",
        "host_java_version": "the value of the //build/bazel/rules/java:host_version setting.",
        "sdk_kind": "the value of the //build/bazel/rules/java/sdk:kind setting.",
        "api_level": "the value of the //build/bazel/rules/java/sdk:api_level setting.",
    },
)

def _sdk_transition_tester_impl(ctx):
    if ctx.attr.exports and len(ctx.attr.exports) > 0 and SdkConfig in ctx.attr.exports[0]:
        return ctx.attr.exports[0][SdkConfig]
    return SdkConfig(
        java_version = ctx.attr._java_version_config_setting[BuildSettingInfo].value,
        host_java_version = ctx.attr._host_java_version_config_setting[BuildSettingInfo].value,
        sdk_kind = ctx.attr._sdk_kind_config_setting[BuildSettingInfo].value,
        api_level = ctx.attr._api_level_config_setting[BuildSettingInfo].value,
    )

sdk_transition_tester = rule(
    implementation = _sdk_transition_tester_impl,
    attrs = {
        "exports": attr.label(
            cfg = sdk_transition,
            providers = [SdkConfig],
        ),
        "java_version": attr.string(),
        "sdk_version": attr.string(),
        "_java_version_config_setting": attr.label(
            default = "//build/bazel/rules/java:version",
        ),
        "_host_java_version_config_setting": attr.label(
            default = "//build/bazel/rules/java:host_version",
        ),
        "_sdk_kind_config_setting": attr.label(
            default = "//build/bazel/rules/java/sdk:kind",
        ),
        "_api_level_config_setting": attr.label(
            default = "//build/bazel/rules/java/sdk:api_level",
        ),
        "_allowlist_function_transition": attr.label(
            default = "@bazel_tools//tools/allowlists/function_transition_allowlist",
        ),
    },
)

def _sdk_transition_host_test_impl(ctx):
    env = analysistest.begin(ctx)
    actual_config = analysistest.target_under_test(env)[SdkConfig]
    asserts.equals(
        env,
        ctx.attr.expected_host_java_version,
        actual_config.host_java_version,
        "mismatching host_java_version",
    )
    return analysistest.end(env)

def _sdk_transition_device_test_impl(ctx):
    env = analysistest.begin(ctx)
    actual_config = analysistest.target_under_test(env)[SdkConfig]
    asserts.equals(
        env,
        ctx.attr.expected_java_version,
        actual_config.java_version,
        "mismatching java_version",
    )
    asserts.equals(
        env,
        ctx.attr.expected_sdk_kind,
        actual_config.sdk_kind,
        "mismatching sdk_kind",
    )
    asserts.equals(
        env,
        ctx.attr.expected_api_level,
        actual_config.api_level,
        "mismatching api_level",
    )
    return analysistest.end(env)

sdk_transition_host_test = analysistest.make(
    impl = _sdk_transition_host_test_impl,
    attrs = {
        "expected_host_java_version": attr.string(),
    },
    config_settings = {
        "//command_line_option:platforms": "@//build/bazel/tests/products:aosp_arm64_for_testing_linux_x86_64",
        "//command_line_option:host_platform": "@//build/bazel/tests/products:aosp_arm64_for_testing_linux_x86_64",
    },
)

sdk_transition_device_test = analysistest.make(
    impl = _sdk_transition_device_test_impl,
    attrs = {
        "expected_java_version": attr.string(),
        "expected_sdk_kind": attr.string(),
        "expected_api_level": attr.int(),
    },
    config_settings = {
        "//command_line_option:platforms": "@//build/bazel/tests/products:aosp_arm64_for_testing",
        "//command_line_option:host_platform": "@//build/bazel/tests/products:aosp_arm64_for_testing_linux_x86_64",
    },
)

def set_up_targets_under_test(name, java_version, sdk_version):
    sdk_transition_tester(
        name = name + "_parent",
        java_version = java_version,
        sdk_version = sdk_version,
        exports = name + "_child",
        tags = ["manual"],
    )
    sdk_transition_tester(
        name = name + "_child",
        tags = ["manual"],
    )

def test_host_sdk_transition(
        name,
        java_version,
        expected_host_java_version):
    set_up_targets_under_test(name, java_version, sdk_version = None)
    sdk_transition_host_test(
        name = name,
        target_under_test = name + "_parent",
        expected_host_java_version = expected_host_java_version,
    )
    return name

def test_device_sdk_transition(
        name,
        java_version,
        sdk_version,
        expected_java_version,
        expected_sdk_kind,
        expected_api_level):
    set_up_targets_under_test(name, java_version, sdk_version)
    sdk_transition_device_test(
        name = name,
        target_under_test = name + "_parent",
        expected_java_version = expected_java_version,
        expected_sdk_kind = expected_sdk_kind,
        expected_api_level = expected_api_level,
    )
    return name

def sdk_transition_test_suite(name):
    native.test_suite(
        name = name,
        tests = [
            test_host_sdk_transition("test_host_sdk_transition", java_version = "8", expected_host_java_version = "8"),
            test_device_sdk_transition("test_device_sdk_transition", java_version = "9", sdk_version = "32", expected_java_version = "9", expected_sdk_kind = "public", expected_api_level = 32),
        ],
    )
