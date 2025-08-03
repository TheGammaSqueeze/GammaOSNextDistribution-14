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
    "//build/bazel/rules/cc/testing:transitions.bzl",
    "compile_action_argv_aspect_generator",
    "link_action_argv_aspect_generator",
    "transition_deps_test_attrs",
    "transition_deps_test_impl",
)
load(":cc_binary.bzl", "cc_binary")
load(":cc_constants.bzl", "transition_constants")

_link_action_argv_aspect = link_action_argv_aspect_generator({
    "versioned_binary": ["src"],
    "cc_binary": ["deps"],
    "stripped_binary": ["src", "unstripped"],
}, "cc_binary")

_compile_action_argv_aspect = compile_action_argv_aspect_generator({
    "versioned_binary": ["src"],
    "_cc_library_combiner": ["deps", "roots", "includes"],
    "_cc_includes": ["deps"],
    "_cc_library_shared_proxy": ["deps"],
    "cc_binary": ["deps"],
    "stripped_binary": ["src", "unstripped"],
})

def _generate_memtag_heap_paths_test(action_argv_aspect, platform):
    return analysistest.make(
        transition_deps_test_impl,
        attrs = transition_deps_test_attrs,
        extra_target_under_test_aspects = [action_argv_aspect],
        config_settings = {
            transition_constants.cli_platforms_key: [platform],
        },
    )

_memtag_heap_sync_include_paths_link_test = _generate_memtag_heap_paths_test(
    _link_action_argv_aspect,
    "@//build/bazel/tests/products:aosp_arm64_for_testing_memtag_heap_sync_include_path",
)

_memtag_heap_sync_include_paths_compile_test = _generate_memtag_heap_paths_test(
    _compile_action_argv_aspect,
    "@//build/bazel/tests/products:aosp_arm64_for_testing_memtag_heap_sync_include_path",
)

_memtag_heap_async_include_paths_link_test = _generate_memtag_heap_paths_test(
    _link_action_argv_aspect,
    "@//build/bazel/tests/products:aosp_arm64_for_testing_memtag_heap_async_include_path",
)

_memtag_heap_async_include_paths_compile_test = _generate_memtag_heap_paths_test(
    _compile_action_argv_aspect,
    "@//build/bazel/tests/products:aosp_arm64_for_testing_memtag_heap_async_include_path",
)

_memtag_heap_exclude_paths_link_test = _generate_memtag_heap_paths_test(
    _link_action_argv_aspect,
    "@//build/bazel/tests/products:aosp_arm64_for_testing_memtag_heap_exclude_path",
)

_memtag_heap_exclude_paths_compile_test = _generate_memtag_heap_paths_test(
    _compile_action_argv_aspect,
    "@//build/bazel/tests/products:aosp_arm64_for_testing_memtag_heap_exclude_path",
)

def _test_memtag_heap_sync_include_paths():
    name = "memtag_heap_sync_include_paths"
    test_name_link = name + "_link_test"
    test_name_compile = name + "_compile_test"

    cc_binary(
        name = name,
        srcs = ["foo.cpp"],
        tags = ["manual"],
    )

    _memtag_heap_sync_include_paths_link_test(
        name = test_name_link,
        target_under_test = name,
        targets_with_flag = [name + "_unstripped"],
        targets_without_flag = [],
        flags = ["-fsanitize=memtag-heap", "-fsanitize-memtag-mode=sync"],
    )

    _memtag_heap_sync_include_paths_compile_test(
        name = test_name_compile,
        target_under_test = name,
        targets_with_flag = [name + "__internal_root_cpp"],
        targets_without_flag = [],
        flags = ["-fsanitize=memtag-heap"],
    )

    return [test_name_link, test_name_compile]

def _test_memtag_heap_async_include_paths():
    name = "memtag_heap_async_include_paths"
    test_name_link = name + "_link_test"
    test_name_compile = name + "_compile_test"

    cc_binary(
        name = name,
        srcs = ["foo.cpp"],
        tags = ["manual"],
    )

    _memtag_heap_async_include_paths_link_test(
        name = test_name_link,
        target_under_test = name,
        targets_with_flag = [name + "_unstripped"],
        targets_without_flag = [],
        flags = ["-fsanitize=memtag-heap", "-fsanitize-memtag-mode=async"],
    )

    _memtag_heap_async_include_paths_compile_test(
        name = test_name_compile,
        target_under_test = name,
        targets_with_flag = [name + "__internal_root_cpp"],
        targets_without_flag = [],
        flags = ["-fsanitize=memtag-heap"],
    )

    return [test_name_link, test_name_compile]

def _test_memtag_heap_exclude_paths():
    name = "memtag_heap_exclude_paths"
    test_name_link = name + "_link_test"
    test_name_compile = name + "_compile_test"

    cc_binary(
        name = name,
        srcs = ["foo.cpp"],
        tags = ["manual"],
    )

    _memtag_heap_exclude_paths_link_test(
        name = test_name_link,
        target_under_test = name,
        targets_with_flag = [],
        targets_without_flag = [name + "_unstripped"],
        flags = ["-fsanitize=memtag-heap", "-fsanitize-memtag-mode=sync", "-fsanitize-memtag-mode=async"],
    )

    _memtag_heap_exclude_paths_compile_test(
        name = test_name_compile,
        target_under_test = name,
        targets_with_flag = [],
        targets_without_flag = [name + "__internal_root_cpp"],
        flags = ["-fsanitize=memtag-heap"],
    )

    return [test_name_link, test_name_compile]

def _test_memtag_heap_passed_in_features_override_include_paths():
    name = "memtag_heap_passed_in_features_override_include_paths"
    test_name = name + "_test"

    cc_binary(
        name = name,
        srcs = ["foo.cpp"],
        features = select({
            "//build/bazel_common_rules/platforms/os_arch:android_arm64": ["-memtag_heap"],
            "//conditions:default": [],
        }),
        tags = ["manual"],
    )

    _memtag_heap_sync_include_paths_link_test(
        name = test_name,
        target_under_test = name,
        targets_with_flag = [],
        targets_without_flag = [name + "_unstripped"],
        flags = ["-fsanitize=memtag-heap", "-fsanitize-memtag-mode=sync", "-fsanitize-memtag-mode=async"],
    )

    return test_name

def memtag_heap_transitions_test_suite(name):
    native.test_suite(
        name = name,
        tests =
            _test_memtag_heap_sync_include_paths() +
            _test_memtag_heap_async_include_paths() +
            _test_memtag_heap_exclude_paths() + [
                _test_memtag_heap_passed_in_features_override_include_paths(),
            ],
    )
