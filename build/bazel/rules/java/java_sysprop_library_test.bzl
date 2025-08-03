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

"""
Tests for java_sysprop_library.bzl
"""

load("@rules_testing//lib:analysis_test.bzl", "analysis_test", "test_suite")
load("@rules_testing//lib:truth.bzl", "matching")
load("//build/bazel/rules/sysprop:sysprop_library.bzl", "sysprop_library")
load(
    "//build/bazel/rules/test_common:paths.bzl",
    "get_output_and_package_dir_based_path_rt",
    "get_package_dir_based_path",
)
load(":java_sysprop_library.bzl", "java_sysprop_library")

def _create_targets_under_test(name):
    sysprop_name = name + "_sysprop"
    sysprop_library(
        name = sysprop_name,
        srcs = [
            "foo.sysprop",
            "bar.sysprop",
        ],
        tags = ["manual"],
    )
    java_sysprop_library(
        name = name,
        dep = sysprop_name,
        tags = ["manual"],
    )

def _java_sysprop_library_target_test(name):
    target_name = name + "_subject"
    _create_targets_under_test(target_name)

    analysis_test(name, target = target_name, impl = _target_test_impl)

def _target_test_impl(env, target):
    target_subject = env.expect.that_target(target)

    output_jar_source_path = get_package_dir_based_path(
        env,
        "java_sysprop_library_target_test_subject.jar",
    )
    target_subject.default_outputs().contains_at_least([output_jar_source_path])
    target_subject.runfiles().contains_at_least([
        "__main__/" + output_jar_source_path,
        "__main__/system/tools/sysprop/libsysprop-library-stub-platform_private.jar",
    ])
    target_subject.has_provider(JavaInfo)

def _java_sysprop_library_java_action_test(name):
    target_name = name + "_subject"
    _create_targets_under_test(target_name)

    analysis_test(name, target = target_name, impl = _java_action_test_impl)

def _java_action_test_impl(env, target):
    stubs_prefix = "system/tools/sysprop/libsysprop-library-stub-platform_private-hjar"

    env.expect.that_target(target).action_named("Javac").inputs().contains_at_least([
        "build/bazel/rules/java/foo.sysprop.srcjar",
        "build/bazel/rules/java/bar.sysprop.srcjar",
        "{}.jar".format(stubs_prefix),
        "{}.jdeps".format(stubs_prefix),
    ])

def _java_sysprop_library_gen_action_args_test(name):
    target_name = name + "_subject"
    _create_targets_under_test(target_name)

    analysis_test(name, target = target_name, impl = _gen_action_args_test_impl)

def _gen_action_args_test_impl(env, target):
    actions = target.actions

    gen_actions = []
    for action in actions:
        if action.mnemonic == "SyspropJava":
            gen_actions.append(action)

    for action in gen_actions:
        name = ""
        if "foo.sysprop" in action.argv[2]:
            name = "foo"
        elif "bar.sysprop" in action.argv[2]:
            name = "bar"
        else:
            fail("neither expected source file was found in an action")

        action_cmds = action.argv[2].split("&&")

        rm_cmd_subject = env.expect.that_collection(
            action_cmds[0].strip().split(" "),
            expr = "rm command for {}".format(name),
        )
        rm_cmd_subject.contains_exactly([
            "rm",
            "-rf",
            get_output_and_package_dir_based_path_rt(
                target,
                "{}.sysprop.srcjar.tmp".format(name),
            ),
        ]).in_order()

        mkdir_cmd_subject = env.expect.that_collection(
            action_cmds[1].strip().split(" "),
            expr = "mkdir command for {}".format(name),
        )
        mkdir_cmd_subject.contains_exactly([
            "mkdir",
            "-p",
            get_output_and_package_dir_based_path_rt(
                target,
                "{}.sysprop.srcjar.tmp".format(name),
            ),
        ]).in_order()

        sysprop_cmd_subject = env.expect.that_collection(
            action_cmds[2].strip().split(" "),
            expr = "sysprop command for {}".format(name),
        )
        sysprop_cmd_subject.contains_exactly_predicates([
            matching.str_endswith(
                "bin/system/tools/sysprop/bin/sysprop_java/sysprop_java",
            ),
            matching.equals_wrapper("--scope"),
            matching.equals_wrapper("internal"),
            matching.equals_wrapper("--java-output-dir"),
            matching.equals_wrapper(get_output_and_package_dir_based_path_rt(
                target,
                "{}.sysprop.srcjar.tmp".format(name),
            )),
            matching.equals_wrapper(get_package_dir_based_path(
                env,
                "{}.sysprop".format(name),
            )),
        ]).in_order()

        expected_out_tmp_dir_path = get_output_and_package_dir_based_path_rt(
            target,
            "{}.sysprop.srcjar.tmp".format(name),
        )
        zip_cmd_subject = env.expect.that_collection(
            action_cmds[3].strip().split(" "),
            expr = "soong_zip command for {}".format(name),
        )
        zip_cmd_subject.contains_exactly_predicates([
            matching.str_endswith(
                "/build/soong/zip/cmd/soong_zip_/soong_zip",
            ),
            matching.equals_wrapper("-jar"),
            matching.equals_wrapper("-o"),
            matching.equals_wrapper(get_output_and_package_dir_based_path_rt(
                target,
                "{}.sysprop.srcjar".format(name),
            )),
            matching.equals_wrapper("-C"),
            matching.equals_wrapper(expected_out_tmp_dir_path),
            matching.equals_wrapper("-D"),
            matching.equals_wrapper(expected_out_tmp_dir_path),
        ])

def _java_sysprop_library_sdk_setting_test(name):
    target_name = name + "_subject"
    _create_targets_under_test(target_name)

    analysis_test(name, target = target_name, impl = _sdk_setting_test_impl)

def _sdk_setting_test_impl(env, target):
    # The argument this check searches for is part of bootclasspath, and is
    # currently the only result of the sdk version transition visible in the
    # java compilation action.
    # core_10000 is the part that reflects the required sdk version,
    # core_current
    # TODO: b/303596698 - Find a better way to test this
    env.expect.that_target(target).action_named(
        "Javac",
    ).argv().contains_at_least_predicates([
        matching.str_endswith(
            "core_10000_android_jar_private/prebuilts/sdk/current/core/android-ijar.jar",
        ),
    ])

def java_sysprop_library_test_suite(name):
    test_suite(
        name = name,
        tests = [
            _java_sysprop_library_gen_action_args_test,
            _java_sysprop_library_java_action_test,
            _java_sysprop_library_target_test,
            _java_sysprop_library_sdk_setting_test,
        ],
    )
