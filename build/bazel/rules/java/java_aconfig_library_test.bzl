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

load("@rules_testing//lib:analysis_test.bzl", "analysis_test", rt_test_suite = "test_suite")
load("@rules_testing//lib:truth.bzl", "matching")
load("@rules_testing//lib:util.bzl", "util")
load("//build/bazel/rules/aconfig:aconfig_declarations.bzl", "aconfig_declarations")
load("//build/bazel/rules/aconfig:aconfig_value_set.bzl", "aconfig_value_set")
load("//build/bazel/rules/aconfig:aconfig_values.bzl", "aconfig_values")
load("//build/bazel/rules/java:java_aconfig_library.bzl", "java_aconfig_library")
load(
    "//build/bazel/rules/test_common:flags.bzl",
    "action_flags_present_for_mnemonic_nonexclusive_test",
    "input_output_verification_test",
)

def test_java_aconfig_library_action():
    name = "java_aconfig_library_action"
    package = "com.android.aconfig.test"
    aconfig_declarations_name = name + "_aconfig_declarations"
    target_under_test = name

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

    java_aconfig_library(
        name = name,
        aconfig_declarations = ":" + aconfig_declarations_name,
        target_compatible_with = ["//build/bazel_common_rules/platforms/os:android"],
        tags = ["manual"],
    )

    test_name_compile_flags = name + "_test_compile_flags"
    action_flags_present_for_mnemonic_nonexclusive_test(
        name = test_name_compile_flags,
        target_under_test = target_under_test,
        mnemonics = [
            "AconfigCreateJavaLib",
        ],
        expected_flags = [
            "create-java-lib",
            "--cache",
            "--out",
            "--mode",
        ],
    )

    test_name_zip_flags = name + "_test_zip_flags"
    action_flags_present_for_mnemonic_nonexclusive_test(
        name = test_name_zip_flags,
        target_under_test = target_under_test,
        mnemonics = [
            "AconfigZipJavaLib",
        ],
        expected_flags = [
            "-write_if_changed",
            "-jar",
            "-o",
            "-C",
            "-D",
            "-symlinks=false",
        ],
    )

    test_name_compile_input_output = name + "_test_compile_input_output"
    input_output_verification_test(
        name = test_name_compile_input_output,
        target_under_test = target_under_test,
        mnemonic = "AconfigCreateJavaLib",
        input_files = [
            "java_aconfig_library_action_aconfig_declarations/intermediate.pb",
        ],
        output_files = [
            "java_aconfig_library_action/gen/tmp",
        ],
    )

    test_name_zip_input_output = name + "_test_zip_input_output"
    input_output_verification_test(
        name = test_name_zip_input_output,
        target_under_test = target_under_test,
        mnemonic = "AconfigZipJavaLib",
        input_files = [
            "java_aconfig_library_action/gen/tmp",
        ],
        output_files = [
            "java_aconfig_library_action/gen/java_aconfig_library_action.srcjar",
        ],
    )

    return [
        test_name_compile_flags,
        test_name_zip_flags,
        test_name_compile_input_output,
        test_name_zip_input_output,
    ]

def _test_java_aconfig_library_rule_rt(name):
    aconfig_declarations_name = name + "_aconfig_declarations"
    package = "com.android.aconfig.test"
    target = name + "_target"

    util.helper_target(
        aconfig_declarations,
        name = aconfig_declarations_name,
        package = package,
        srcs = ["test.aconfig"],
        tags = ["manual"],
    )
    util.helper_target(
        java_aconfig_library,
        name = target,
        aconfig_declarations = ":" + aconfig_declarations_name,
        target_compatible_with = ["//build/bazel_common_rules/platforms/os:android"],
        tags = ["manual"],
    )
    analysis_test(
        name = name,
        impl = _test_java_aconfig_library_rule_rt_impl,
        target = target,
    )

def _test_java_aconfig_library_rule_rt_impl(env, target):
    for mnemonic in [
        "AconfigCreateJavaLib",
        "AconfigZipJavaLib",
    ]:
        env.expect.that_target(target).action_named(mnemonic).mnemonic().equals(mnemonic)

    env.expect.that_target(target).default_outputs().contains_predicate(
        matching.file_basename_equals(target.label.name + ".jar"),
    )

    # Providers
    env.expect.that_target(target).has_provider(JavaInfo)
    env.expect.that_target(target).output_group("srcjar").contains_predicate(
        matching.file_basename_equals(target.label.name + ".srcjar"),
    )

def java_aconfig_library_test_suite(name):
    native.test_suite(
        name = name,
        tests = test_java_aconfig_library_action(),
    )

def java_aconfig_library_rt_test_suite(name):
    rt_test_suite(
        name = name,
        tests = [_test_java_aconfig_library_rule_rt],
    )
