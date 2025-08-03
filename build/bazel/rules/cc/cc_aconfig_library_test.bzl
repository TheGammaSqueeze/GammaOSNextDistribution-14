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
load("//build/bazel/rules/cc:cc_aconfig_library.bzl", "cc_aconfig_library")
load(
    "//build/bazel/rules/test_common:flags.bzl",
    "action_flags_present_for_mnemonic_nonexclusive_test",
    "input_output_verification_test",
)

def test_cc_aconfig_library_action():
    name = "cc_aconfig_library_action"
    package = "com.android.aconfig.test"
    aconfig_declarations_name = name + "_aconfig_declarations"

    aconfig_value_set(
        name = "aconfig.test.value_set",
        values = [":aconfig.test.values"],
        tags = ["manual"],
    )

    aconfig_values(
        name = "aconfig.test.values",
        package = package,
        srcs = [
            "test.textproto",
        ],
        tags = ["manual"],
    )

    aconfig_declarations(
        name = aconfig_declarations_name,
        package = package,
        srcs = ["test.aconfig"],
        tags = ["manual"],
    )

    cc_aconfig_library(
        name = name,
        aconfig_declarations = ":" + aconfig_declarations_name,
        dynamic_deps = ["//system/server_configurable_flags/libflags:server_configurable_flags"],
        target_compatible_with = ["//build/bazel_common_rules/platforms/os:android"],
        tags = ["manual"],
    )

    test_name_flags = name + "_test_flags"
    action_flags_present_for_mnemonic_nonexclusive_test(
        name = test_name_flags,
        target_under_test = name + "_gen",
        mnemonics = [
            "AconfigCreateCppLib",
        ],
        expected_flags = [
            "create-cpp-lib",
            "--cache",
            "--out",
        ],
    )

    test_name_input_output = name + "_test_input_output"
    input_output_verification_test(
        name = test_name_input_output,
        target_under_test = name + "_gen",
        mnemonic = "AconfigCreateCppLib",
        input_files = [
            "cc_aconfig_library_action_aconfig_declarations/intermediate.pb",
        ],
        output_files = [
            "cc_aconfig_library_action_gen/gen/com_android_aconfig_test.cc",
            "cc_aconfig_library_action_gen/gen/include/com_android_aconfig_test.h",
        ],
    )

    return [test_name_flags, test_name_input_output]

def cc_aconfig_library_test_suite(name):
    native.test_suite(
        name = name,
        tests = test_cc_aconfig_library_action(),
    )
