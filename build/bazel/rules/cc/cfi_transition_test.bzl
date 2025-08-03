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

load("@bazel_skylib//lib:unittest.bzl", "analysistest")
load(
    "@soong_injection//cc_toolchain:sanitizer_constants.bzl",
    _generated_sanitizer_constants = "constants",
)
load(
    "//build/bazel/rules/cc/testing:transitions.bzl",
    "compile_action_argv_aspect_generator",
    "transition_deps_test_attrs",
    "transition_deps_test_impl",
)
load(":cc_binary.bzl", "cc_binary")
load(":cc_constants.bzl", "transition_constants")
load(":cc_library_shared.bzl", "cc_library_shared")
load(":cc_library_static.bzl", "cc_library_static")

_compile_action_argv_aspect = compile_action_argv_aspect_generator({
    "_cc_library_combiner": ["deps", "roots", "includes"],
    "_cc_includes": ["deps"],
    "_cc_library_shared_proxy": ["deps"],
    "cc_binary": ["deps"],
    "stripped_binary": ["src", "unstripped"],
})

_cfi_deps_test = analysistest.make(
    transition_deps_test_impl,
    attrs = transition_deps_test_attrs,
    extra_target_under_test_aspects = [_compile_action_argv_aspect],
)

cfi_feature = "android_cfi"
static_cpp_suffix = "_cpp"
shared_or_binary_cpp_suffix = "__internal_root_cpp"
binary_suffix = "__internal_root"

def _test_cfi_propagates_to_static_deps():
    name = "cfi_propagates_to_static_deps"
    static_dep_name = name + "_static_dep"
    static_dep_of_static_dep_name = static_dep_name + "_of_static_dep"
    test_name = name + "_test"

    cc_library_shared(
        name = name,
        srcs = ["foo.cpp"],
        deps = [static_dep_name],
        features = [cfi_feature],
        tags = ["manual"],
    )

    cc_library_static(
        name = static_dep_name,
        srcs = ["bar.cpp"],
        deps = [static_dep_of_static_dep_name],
        tags = ["manual"],
    )

    cc_library_static(
        name = static_dep_of_static_dep_name,
        srcs = ["baz.cpp"],
        tags = ["manual"],
    )

    _cfi_deps_test(
        name = test_name,
        target_under_test = name,
        flags = [_generated_sanitizer_constants.CfiCrossDsoFlag],
        targets_with_flag = [
            name + shared_or_binary_cpp_suffix,
            static_dep_name + static_cpp_suffix,
            static_dep_of_static_dep_name + static_cpp_suffix,
        ],
        targets_without_flag = [],
    )

    return test_name

def _test_cfi_does_not_propagate_to_shared_deps():
    name = "cfi_does_not_propagate_to_shared_deps"
    shared_dep_name = name + "shared_dep"
    test_name = name + "_test"

    cc_library_shared(
        name = name,
        srcs = ["foo.cpp"],
        deps = [shared_dep_name],
        features = [cfi_feature],
        tags = ["manual"],
    )

    cc_library_shared(
        name = shared_dep_name,
        srcs = ["bar.cpp"],
        tags = ["manual"],
    )

    _cfi_deps_test(
        name = test_name,
        target_under_test = name,
        flags = [_generated_sanitizer_constants.CfiCrossDsoFlag],
        targets_with_flag = [name + shared_or_binary_cpp_suffix],
        targets_without_flag = [shared_dep_name + shared_or_binary_cpp_suffix],
    )

    return test_name

def _test_cfi_disabled_propagates_to_static_deps():
    name = "cfi_disabled_propagates_to_static_deps"
    static_dep_name = name + "_static_dep"
    test_name = name + "_test"

    cc_library_shared(
        name = name,
        srcs = ["foo.cpp"],
        deps = [static_dep_name],
        tags = ["manual"],
    )

    cc_library_static(
        name = static_dep_name,
        srcs = ["bar.cpp"],
        features = ["android_cfi"],
        tags = ["manual"],
    )

    _cfi_deps_test(
        name = test_name,
        target_under_test = name,
        flags = [_generated_sanitizer_constants.CfiCrossDsoFlag],
        targets_with_flag = [],
        targets_without_flag = [
            name + shared_or_binary_cpp_suffix,
            static_dep_name + static_cpp_suffix,
        ],
    )

    return test_name

def _test_cfi_binary_propagates_to_static_deps():
    name = "cfi_binary_propagates_to_static_deps"
    static_dep_name = name + "_static_dep"
    test_name = name + "_test"

    cc_binary(
        name = name,
        srcs = ["foo.cpp"],
        deps = [static_dep_name],
        features = [cfi_feature],
        tags = ["manual"],
    )

    cc_library_static(
        name = static_dep_name,
        srcs = ["bar.cpp"],
        tags = ["manual"],
    )

    _cfi_deps_test(
        name = test_name,
        target_under_test = name,
        flags = [_generated_sanitizer_constants.CfiCrossDsoFlag],
        targets_with_flag = [
            name + shared_or_binary_cpp_suffix,
            static_dep_name + static_cpp_suffix,
        ],
        targets_without_flag = [],
    )

    return test_name

_cfi_deps_cfi_include_paths_test = analysistest.make(
    transition_deps_test_impl,
    attrs = transition_deps_test_attrs,
    extra_target_under_test_aspects = [_compile_action_argv_aspect],
    config_settings = {
        transition_constants.cli_platforms_key: [
            "@//build/bazel/tests/products:aosp_x86_for_testing_cfi_include_path",
        ],
    },
)

def _test_cfi_include_paths_enables_cfi_for_device():
    name = "cfi_include_paths_enables_cfi_for_device"
    test_name = name + "_test"

    cc_library_shared(
        name = name,
        srcs = ["foo.cpp"],
        tags = ["manual"],
    )

    _cfi_deps_cfi_include_paths_test(
        name = test_name,
        target_under_test = name,
        flags = [_generated_sanitizer_constants.CfiCrossDsoFlag],
        targets_with_flag = [name + shared_or_binary_cpp_suffix],
        targets_without_flag = [],
    )

    return test_name

_cfi_deps_cfi_includes_paths_host_no_cfi_test = analysistest.make(
    transition_deps_test_impl,
    attrs = transition_deps_test_attrs,
    extra_target_under_test_aspects = [_compile_action_argv_aspect],
    config_settings = {
        transition_constants.cli_platforms_key: [
            "@//build/bazel/tests/products:aosp_x86_for_testing_cfi_include_path_linux_x86",
        ],
    },
)

def _test_cfi_include_paths_host_no_cfi():
    name = "cfi_include_paths_host_no_cfi"
    test_name = name + "_test"

    cc_library_shared(
        name = name,
        srcs = ["foo.cpp"],
        tags = ["manual"],
    )

    _cfi_deps_cfi_includes_paths_host_no_cfi_test(
        name = test_name,
        target_under_test = name,
        flags = [_generated_sanitizer_constants.CfiCrossDsoFlag],
        targets_with_flag = [],
        targets_without_flag = [name + shared_or_binary_cpp_suffix],
    )

    return test_name

_cfi_exclude_paths_no_cfi_test = analysistest.make(
    transition_deps_test_impl,
    attrs = transition_deps_test_attrs,
    extra_target_under_test_aspects = [_compile_action_argv_aspect],
    config_settings = {
        transition_constants.cli_platforms_key: [
            "@//build/bazel/tests/products:aosp_x86_for_testing_cfi_exclude_path",
        ],
    },
)

def _test_cfi_exclude_paths_disable_cfi():
    name = "cfi_exclude_paths_disable_cfi"
    test_name = name + "_test"

    cc_library_shared(
        name = name,
        srcs = ["foo.cpp"],
        features = ["android_cfi"],
        tags = ["manual"],
    )

    _cfi_exclude_paths_no_cfi_test(
        name = test_name,
        target_under_test = name,
        flags = [_generated_sanitizer_constants.CfiCrossDsoFlag],
        targets_with_flag = [],
        targets_without_flag = [name + shared_or_binary_cpp_suffix],
    )

    return test_name

_enable_cfi_false_no_cfi_test = analysistest.make(
    transition_deps_test_impl,
    attrs = transition_deps_test_attrs,
    extra_target_under_test_aspects = [_compile_action_argv_aspect],
    config_settings = {
        transition_constants.enable_cfi_key: False,
    },
)

def _test_enable_cfi_false_disables_cfi_globally():
    name = "enable_cfi_false_disables_cfi_globally"
    test_name = name + "_test"

    cc_library_shared(
        name = name,
        srcs = ["foo.cpp"],
        features = ["android_cfi"],
        tags = ["manual"],
    )

    _enable_cfi_false_no_cfi_test(
        name = test_name,
        target_under_test = name,
        flags = [_generated_sanitizer_constants.CfiCrossDsoFlag],
        targets_with_flag = [],
        targets_without_flag = [name + shared_or_binary_cpp_suffix],
    )

    return test_name

def cfi_transition_test_suite(name):
    native.test_suite(
        name = name,
        tests = [
            _test_cfi_propagates_to_static_deps(),
            _test_cfi_does_not_propagate_to_shared_deps(),
            _test_cfi_disabled_propagates_to_static_deps(),
            _test_cfi_binary_propagates_to_static_deps(),
            _test_cfi_include_paths_enables_cfi_for_device(),
            _test_cfi_include_paths_host_no_cfi(),
            _test_cfi_exclude_paths_disable_cfi(),
            _test_enable_cfi_false_disables_cfi_globally(),
        ],
    )
