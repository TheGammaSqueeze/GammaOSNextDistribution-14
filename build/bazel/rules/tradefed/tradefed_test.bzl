# Copyright (C) 2022 The Android Open Source Project
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

load("@bazel_skylib//lib:paths.bzl", "paths")
load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts")
load("//build/bazel/rules/cc:cc_binary.bzl", "cc_binary")
load("//build/bazel/rules/cc:cc_library_shared.bzl", "cc_library_shared")
load("//build/bazel/rules/cc:cc_library_static.bzl", "cc_library_static")
load(
    "//build/bazel/rules/test_common:paths.bzl",
    "get_output_and_package_dir_based_path",
)
load(":tradefed.bzl", "tradefed_device_driven_test", "tradefed_deviceless_test", "tradefed_host_driven_device_test")

def _test_tradefed_config_generation_impl(ctx):
    env = analysistest.begin(ctx)
    actions = analysistest.target_actions(env)

    actual_outputs = []
    for action in actions:
        for output in action.outputs.to_list():
            actual_outputs.append(output.path)

    for expected_output in ctx.attr.expected_outputs:
        expected_output = get_output_and_package_dir_based_path(env, expected_output)
        asserts.true(
            env,
            expected_output in actual_outputs,
            "Expected: " + expected_output +
            " in outputs: " + str(actual_outputs),
        )
    return analysistest.end(env)

tradefed_config_generation_test = analysistest.make(
    _test_tradefed_config_generation_impl,
    attrs = {
        "expected_outputs": attr.string_list(),
    },
)

def tradefed_cc_outputs():
    name = "cc"
    target = "cc_target"
    dep_name = name + "_lib"

    cc_library_static(
        name = dep_name,
        tags = ["manual"],
    )
    native.cc_test(
        name = target,
        deps = [dep_name],
        tags = ["manual"],
    )
    tradefed_device_driven_test(
        name = name,
        tags = ["manual"],
        test = target,
        test_config = "//build/bazel/rules/tradefed/test:example_config.xml",
        dynamic_config = "//build/bazel/rules/tradefed/test:dynamic_config.xml",
        target_compatible_with = ["//build/bazel_common_rules/platforms/os:linux"],
    )

    # check for expected output files (.config file  and .sh script)
    tradefed_config_generation_test(
        name = name + "_test",
        target_under_test = name,
        expected_outputs = [
            name + ".sh",
            "result-reporters.xml",
            paths.join(name, "testcases", target + ".config"),
            paths.join(name, "testcases", target + ".dynamic"),
        ],
        target_compatible_with = ["//build/bazel_common_rules/platforms/os:linux"],
    )
    return name + "_test"

def tradefed_cc_host_outputs():
    name = "cc_host"
    target = "cc_host_target"
    dep_name = name + "_lib"

    cc_library_static(
        name = dep_name,
        tags = ["manual"],
    )
    native.cc_test(
        name = target,
        deps = [dep_name],
        tags = ["manual"],
    )
    tradefed_host_driven_device_test(
        name = name,
        tags = ["manual"],
        test = target,
        test_config = "//build/bazel/rules/tradefed/test:example_config.xml",
        dynamic_config = "//build/bazel/rules/tradefed/test:dynamic_config.xml",
        target_compatible_with = ["//build/bazel_common_rules/platforms/os:linux"],
    )

    # check for expected output files (.config file  and .sh script)
    tradefed_config_generation_test(
        name = name + "_test",
        target_under_test = name,
        expected_outputs = [
            name + ".sh",
            "result-reporters.xml",
            paths.join(name, "testcases", target + ".config"),
            paths.join(name, "testcases", target + ".dynamic"),
        ],
        target_compatible_with = ["//build/bazel_common_rules/platforms/os:linux"],
    )
    return name + "_test"

def tradefed_cc_host_outputs_generate_test_config():
    name = "cc_host_generate_config"
    target = "cc_host_target_generate_config"
    dep_name = name + "_lib"

    cc_library_static(
        name = dep_name,
        tags = ["manual"],
    )
    native.cc_test(
        name = target,
        deps = [dep_name],
        tags = ["manual"],
    )
    tradefed_host_driven_device_test(
        name = name,
        tags = ["manual"],
        test = target,
        template_test_config = "//build/make/core:native_host_test_config_template.xml",
        template_configs = [
            "<option name=\"config-descriptor:metadata\" key=\"parameter\" value=\"not_multi_abi\" />",
            "<option name=\"config-descriptor:metadata\" key=\"parameter\" value=\"secondary_user\" />",
        ],
        dynamic_config = "//build/bazel/rules/tradefed/test:dynamic_config.xml",
        target_compatible_with = ["//build/bazel_common_rules/platforms/os:linux"],
    )

    # check for expected output files (.config file  and .sh script)
    tradefed_config_generation_test(
        name = name + "_test",
        target_under_test = name,
        expected_outputs = [
            name + ".sh",
            "result-reporters.xml",
            paths.join(name, "testcases", target + ".config"),
            paths.join(name, "testcases", target + ".dynamic"),
        ],
        target_compatible_with = ["//build/bazel_common_rules/platforms/os:linux"],
    )
    return name + "_test"

def _tradefed_cc_copy_runfiles_test_impl(ctx):
    env = analysistest.begin(ctx)
    target = analysistest.target_under_test(env)
    actions = analysistest.target_actions(env)
    copy_actions = [a for a in actions if a.mnemonic == "CopyFile"]
    outputs = []
    for action in copy_actions:
        for output in action.outputs.to_list():
            outputs.append(output.path)

    for expect in ctx.attr.expected_files:
        expect = get_output_and_package_dir_based_path(env, paths.join(target.label.name, "testcases", expect))
        asserts.true(
            env,
            expect in outputs,
            "Expected: " + expect +
            " in outputs: " + str(outputs),
        )

    return analysistest.end(env)

tradefed_cc_copy_runfiles_test = analysistest.make(
    _tradefed_cc_copy_runfiles_test_impl,
    attrs = {
        "expected_files": attr.string_list(
            doc = "Files should be copied.",
        ),
    },
    config_settings = {
        "//command_line_option:platforms": "@//build/bazel/tests/products:aosp_x86_64_for_testing",
    },
)

def tradefed_cc_copy_runfiles():
    name = "tradefed_cc_copy_runfiles"
    test_name = name + "_test"

    cc_library_shared(
        name = name + "_shared_lib_1",
        srcs = [name + "_shared_lib_1.cc"],
        dynamic_deps = [name + "_shared_lib_2"],
        tags = ["manual"],
    )

    cc_library_shared(
        name = name + "_shared_lib_2",
        srcs = [name + "_shared_lib_2.cc"],
        tags = ["manual"],
    )

    cc_library_shared(
        name = name + "_shared_lib_3",
        srcs = [name + "_shared_lib_3.cc"],
        tags = ["manual"],
    )

    cc_library_shared(
        name = name + "_shared_lib_4",
        srcs = [name + "_shared_lib_4.cc"],
        tags = ["manual"],
    )

    cc_library_static(
        name = name + "_static_lib_1",
        srcs = [name + "_static_lib_1.cc"],
        dynamic_deps = [name + "_shared_lib_3"],
        tags = ["manual"],
    )

    cc_binary(
        name = name + "__tf_internal",
        generate_cc_test = True,
        deps = [name + "_static_lib_1"],
        dynamic_deps = [name + "_shared_lib_1"],
        runtime_deps = [name + "_shared_lib_4"],
        data = ["data/a.text"],
        tags = ["manual"],
    )

    tradefed_deviceless_test(
        name = name,
        tags = ["manual"],
        test = name + "__tf_internal",
        test_config = "//build/bazel/rules/tradefed/test:example_config.xml",
        dynamic_config = "//build/bazel/rules/tradefed/test:dynamic_config.xml",
    )

    tradefed_cc_copy_runfiles_test(
        name = test_name,
        target_under_test = name,
        expected_files = [
            "tradefed_cc_copy_runfiles.config",
            "tradefed_cc_copy_runfiles.dynamic",
            "tradefed_cc_copy_runfiles",
            "data/a.text",
            "lib64/tradefed_cc_copy_runfiles_shared_lib_1.so",
            "lib64/tradefed_cc_copy_runfiles_shared_lib_2.so",
            "lib64/tradefed_cc_copy_runfiles_shared_lib_3.so",
            "lib64/tradefed_cc_copy_runfiles_shared_lib_4.so",
        ],
    )

    return test_name

def tradefed_cc_copy_runfiles_with_suffix():
    name = "tradefed_cc_copy_runfiles_with_suffix"
    test_name = name + "_test"
    suffix = "64"

    cc_library_shared(
        name = name + "_shared_lib_1",
        srcs = [name + "_shared_lib_1.cc"],
        tags = ["manual"],
    )

    cc_binary(
        name = name + "__tf_internal",
        generate_cc_test = True,
        dynamic_deps = [name + "_shared_lib_1"],
        data = ["data/a.text"],
        tags = ["manual"],
        suffix = suffix,
    )

    tradefed_deviceless_test(
        name = name,
        tags = ["manual"],
        test = name + "__tf_internal",
        test_config = "//build/bazel/rules/tradefed/test:example_config.xml",
        dynamic_config = "//build/bazel/rules/tradefed/test:dynamic_config.xml",
        suffix = suffix,
    )

    tradefed_cc_copy_runfiles_test(
        name = test_name,
        target_under_test = name,
        expected_files = [
            "tradefed_cc_copy_runfiles_with_suffix64.config",
            "tradefed_cc_copy_runfiles_with_suffix64.dynamic",
            "tradefed_cc_copy_runfiles_with_suffix64",
            "data/a.text",
            "lib64/tradefed_cc_copy_runfiles_with_suffix_shared_lib_1.so",
        ],
    )

    return test_name

def _tradefed_cc_compat_suffix_test_impl(ctx):
    env = analysistest.begin(ctx)
    target = analysistest.target_under_test(env)
    actions = analysistest.target_actions(env)
    symlink_actions = [a for a in actions if a.mnemonic == "Symlink"]
    outputs = []
    for action in symlink_actions:
        for output in action.outputs.to_list():
            outputs.append(output.path)

    for expect in ctx.attr.expected_files:
        expect = get_output_and_package_dir_based_path(env, paths.join(target.label.name, "testcases", expect))
        asserts.true(
            env,
            expect in outputs,
            "Expected: " + expect +
            " in outputs: " + str(outputs),
        )

    return analysistest.end(env)

tradefed_cc_compat_suffix_test = analysistest.make(
    _tradefed_cc_compat_suffix_test_impl,
    attrs = {
        "expected_files": attr.string_list(
            doc = "Files to be symlinked",
        ),
    },
    config_settings = {
        "//command_line_option:platforms": "@//build/bazel/tests/products:aosp_x86_64_for_testing",
    },
)

def tradefed_cc_test_suffix_has_suffixless_compat_symlink():
    name = "tradefed_cc_test_suffix_has_suffixless_compat_symlink"
    test_name = name + "_test"
    suffix = "64"

    cc_binary(
        name = name + "__tf_internal",
        generate_cc_test = True,
        tags = ["manual"],
        suffix = suffix,
    )

    tradefed_deviceless_test(
        name = name,
        tags = ["manual"],
        test = name + "__tf_internal",
        test_config = "//build/bazel/rules/tradefed/test:example_config.xml",
        suffix = suffix,
    )

    tradefed_cc_compat_suffix_test(
        name = test_name,
        target_under_test = name,
        expected_files = [
            "tradefed_cc_test_suffix_has_suffixless_compat_symlink",
        ],
    )

    return test_name

def tradefed_test_suite(name):
    native.test_suite(
        name = name,
        tests = [
            tradefed_cc_outputs(),
            tradefed_cc_host_outputs(),
            tradefed_cc_host_outputs_generate_test_config(),
            tradefed_cc_copy_runfiles(),
            tradefed_cc_copy_runfiles_with_suffix(),
            tradefed_cc_test_suffix_has_suffixless_compat_symlink(),
        ],
    )
