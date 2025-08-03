"""Copyright (C) 2023 The Android Open Source Project

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

load("//build/bazel/rules/aconfig:aconfig_declarations.bzl", "aconfig_declarations")
load("//build/bazel/rules/aconfig:aconfig_value_set.bzl", "aconfig_value_set")
load("//build/bazel/rules/aconfig:aconfig_values.bzl", "aconfig_values")
load(
    "//build/bazel/rules/test_common:flags.bzl",
    "action_flags_present_only_for_mnemonic_test_with_config_settings",
)

action_flags_present_only_for_aconfig_declarations_test = action_flags_present_only_for_mnemonic_test_with_config_settings({
    "//command_line_option:platforms": "@//build/bazel/tests/products:aosp_arm64_for_testing_aconfig_release",
})

def test_aconfig_declarations_action():
    name = "aconfig_declarations_action"
    test_name = name + "_test"
    package = "com.android.aconfig.test"

    aconfig_value_set(
        name = "aconfig.test.value_set1",
        values = [":aconfig.test.values1"],
        visibility = ["//visibility:public"],
        tags = ["manual"],
    )

    aconfig_value_set(
        name = "aconfig.test.value_set2",
        values = [":aconfig.test.values2"],
        visibility = ["//visibility:public"],
        tags = ["manual"],
    )

    aconfig_values(
        name = "aconfig.test.values1",
        package = package,
        srcs = [
            "test1.textproto",
        ],
        tags = ["manual"],
    )

    aconfig_values(
        name = "aconfig.test.values2",
        package = package,
        srcs = [
            "test2.textproto",
        ],
        tags = ["manual"],
    )

    aconfig_declarations(
        name = name,
        package = package,
        srcs = ["test.aconfig"],
        tags = ["manual"],
    )

    action_flags_present_only_for_aconfig_declarations_test(
        name = test_name,
        target_under_test = name,
        mnemonics = [
            "AconfigCreateCache",
        ],
        expected_flags = [
            "create-cache",
            "--package",
            "com.android.aconfig.test",
            "--declarations",
            "build/bazel/rules/aconfig/test.aconfig",
            "--values",
            "build/bazel/rules/aconfig/test1.textproto",
            "build/bazel/rules/aconfig/test2.textproto",
            "--default-permission",
            "READ_WRITE",
            "--cache",
        ],
    )

    return test_name

def aconfig_declarations_test_suite(name):
    native.test_suite(
        name = name,
        tests = [
            test_aconfig_declarations_action(),
        ],
    )
