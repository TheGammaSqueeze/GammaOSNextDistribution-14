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

# This logic checks for the enablement of sanitizers to update the relevant
# config_setting for the purpose of controlling the addition of sanitizer
# blocklists.

load("@bazel_skylib//lib:unittest.bzl", "analysistest")
load("//build/bazel/rules/cc:cc_binary.bzl", "cc_binary")
load("//build/bazel/rules/cc:cc_library_shared.bzl", "cc_library_shared")
load("//build/bazel/rules/cc:cc_library_static.bzl", "cc_library_static")
load(
    "//build/bazel/rules/cc/testing:transitions.bzl",
    "compile_action_argv_aspect_generator",
    "transition_deps_test_attrs",
    "transition_deps_test_impl",
)

static_cpp_suffix = "_cpp"
shared_and_binary_cpp_suffix = "__internal_root_cpp"

_compile_action_argv_aspect = compile_action_argv_aspect_generator({
    "_cc_library_combiner": ["deps", "roots", "includes"],
    "_cc_includes": ["deps"],
    "_cc_library_shared_proxy": ["deps"],
    "stripped_binary": ["androidmk_deps"],
})

sanitizer_blocklist_test = analysistest.make(
    transition_deps_test_impl,
    attrs = transition_deps_test_attrs,
    extra_target_under_test_aspects = [_compile_action_argv_aspect],
)

sanitizer_blocklist_name = "foo_blocklist.txt"
sanitizer_blocklist_flag = (
    "-fsanitize-ignorelist=build/bazel/rules/cc/" +
    sanitizer_blocklist_name
)

# TODO: b/294868620 - This select can be made into a normal list when completing
#                     the bug
sanitizer_blocklist_select = select({
    "//build/bazel/rules/cc:sanitizers_enabled": [sanitizer_blocklist_flag],
    "//conditions:default": [],
})

def test_sanitizer_blocklist_with_ubsan_static():
    name = "sanitizer_blocklist_with_ubsan_static"
    cc_library_static(
        name = name,
        srcs = ["foo.cpp"],
        features = ["ubsan_integer"],
        copts = sanitizer_blocklist_select,
        additional_compiler_inputs = [sanitizer_blocklist_name],
        tags = ["manual"],
    )

    test_name = name + "_test"
    sanitizer_blocklist_test(
        name = test_name,
        target_under_test = name,
        targets_with_flag = [name + static_cpp_suffix],
        flags = [sanitizer_blocklist_flag],
    )

    return test_name

def test_sanitizer_blocklist_with_cfi_static():
    name = "sanitizer_blocklist_with_cfi_static"
    cc_library_static(
        name = name,
        srcs = ["foo.cpp"],
        features = ["android_cfi"],
        copts = sanitizer_blocklist_select,
        additional_compiler_inputs = [sanitizer_blocklist_name],
        tags = ["manual"],
    )

    test_name = name + "_test"
    sanitizer_blocklist_test(
        name = test_name,
        target_under_test = name,
        targets_with_flag = [name + static_cpp_suffix],
        flags = [sanitizer_blocklist_flag],
    )

    return test_name

def test_no_sanitizer_blocklist_without_sanitizer_static():
    name = "no_sanitizer_blocklist_without_sanitizer_static"
    cc_library_static(
        name = name,
        srcs = ["foo.cpp"],
        copts = sanitizer_blocklist_select,
        additional_compiler_inputs = [sanitizer_blocklist_name],
        tags = ["manual"],
    )

    test_name = name + "_test"
    sanitizer_blocklist_test(
        name = test_name,
        target_under_test = name,
        targets_without_flag = [name + static_cpp_suffix],
        flags = [sanitizer_blocklist_flag],
    )

    return test_name

def test_sanitizer_blocklist_with_ubsan_shared():
    name = "sanitizer_blocklist_with_ubsan_shared"
    cc_library_shared(
        name = name,
        srcs = ["foo.cpp"],
        features = ["ubsan_integer"],
        copts = sanitizer_blocklist_select,
        additional_compiler_inputs = [sanitizer_blocklist_name],
        tags = ["manual"],
    )

    test_name = name + "_test"
    sanitizer_blocklist_test(
        name = test_name,
        target_under_test = name,
        targets_with_flag = [name + shared_and_binary_cpp_suffix],
        flags = [sanitizer_blocklist_flag],
    )

    return test_name

def test_sanitizer_blocklist_with_cfi_shared():
    name = "sanitizer_blocklist_with_cfi_shared"
    cc_library_shared(
        name = name,
        srcs = ["foo.cpp"],
        features = ["android_cfi"],
        copts = sanitizer_blocklist_select,
        additional_compiler_inputs = [sanitizer_blocklist_name],
        tags = ["manual"],
    )

    test_name = name + "_test"
    sanitizer_blocklist_test(
        name = test_name,
        target_under_test = name,
        targets_with_flag = [name + shared_and_binary_cpp_suffix],
        flags = [sanitizer_blocklist_flag],
    )

    return test_name

def test_no_sanitizer_blocklist_without_sanitizer_shared():
    name = "no_sanitizer_blocklist_without_sanitizer_shared"
    cc_library_shared(
        name = name,
        srcs = ["foo.cpp"],
        copts = sanitizer_blocklist_select,
        additional_compiler_inputs = [sanitizer_blocklist_name],
        tags = ["manual"],
    )

    test_name = name + "_test"
    sanitizer_blocklist_test(
        name = test_name,
        target_under_test = name,
        targets_without_flag = [name + shared_and_binary_cpp_suffix],
        flags = [sanitizer_blocklist_flag],
    )

    return test_name

def test_sanitizer_blocklist_with_ubsan_binary():
    name = "sanitizer_blocklist_with_ubsan_binary"
    cc_binary(
        name = name,
        srcs = ["foo.cpp"],
        features = ["ubsan_integer"],
        copts = sanitizer_blocklist_select,
        additional_compiler_inputs = [sanitizer_blocklist_name],
        tags = ["manual"],
    )

    test_name = name + "_test"
    sanitizer_blocklist_test(
        name = test_name,
        target_under_test = name,
        targets_with_flag = [name + shared_and_binary_cpp_suffix],
        flags = [sanitizer_blocklist_flag],
    )

    return test_name

def test_sanitizer_blocklist_with_cfi_binary():
    name = "sanitizer_blocklist_with_cfi_binary"
    cc_binary(
        name = name,
        srcs = ["foo.cpp"],
        features = ["android_cfi"],
        copts = sanitizer_blocklist_select,
        additional_compiler_inputs = [sanitizer_blocklist_name],
        tags = ["manual"],
    )

    test_name = name + "_test"
    sanitizer_blocklist_test(
        name = test_name,
        target_under_test = name,
        targets_with_flag = [name + shared_and_binary_cpp_suffix],
        flags = [sanitizer_blocklist_flag],
    )

    return test_name

def test_no_sanitizer_blocklist_without_sanitizer_binary():
    name = "no_sanitizer_blocklist_without_sanitizer_binary"
    cc_binary(
        name = name,
        srcs = ["foo.cpp"],
        copts = sanitizer_blocklist_select,
        additional_compiler_inputs = [sanitizer_blocklist_name],
        tags = ["manual"],
    )

    test_name = name + "_test"
    sanitizer_blocklist_test(
        name = test_name,
        target_under_test = name,
        targets_without_flag = [name + shared_and_binary_cpp_suffix],
        flags = [sanitizer_blocklist_flag],
    )

    return test_name

def test_sanitizer_blocklist_on_dep_with_cfi():
    name = "sanitizer_blocklist_on_dep_with_cfi"
    requested_target_name = name + "_requested_target"
    dep_name = name + "_dep"
    cc_library_shared(
        name = requested_target_name,
        deps = [dep_name],
        features = ["android_cfi"],
        tags = ["manual"],
    )
    cc_library_static(
        name = dep_name,
        srcs = ["foo.cpp"],
        copts = sanitizer_blocklist_select,
        additional_compiler_inputs = [sanitizer_blocklist_name],
        tags = ["manual"],
    )

    test_name = name + "_test"
    sanitizer_blocklist_test(
        name = test_name,
        target_under_test = requested_target_name,
        targets_with_flag = [dep_name + static_cpp_suffix],
        flags = [sanitizer_blocklist_flag],
    )

    return test_name

def test_no_sanitizer_blocklist_on_dep_without_cfi():
    name = "no_sanitizer_blocklist_on_dep_without_cfi"
    requested_target_name = name + "_requested_target"
    dep_name = name + "_dep"
    cc_library_shared(
        name = requested_target_name,
        deps = [dep_name],
        tags = ["manual"],
    )
    cc_library_static(
        name = dep_name,
        srcs = ["foo.cpp"],
        copts = sanitizer_blocklist_select,
        additional_compiler_inputs = [sanitizer_blocklist_name],
        tags = ["manual"],
    )

    test_name = name + "_test"
    sanitizer_blocklist_test(
        name = test_name,
        target_under_test = requested_target_name,
        targets_without_flag = [dep_name + static_cpp_suffix],
        flags = [sanitizer_blocklist_flag],
    )

    return test_name

# We test this because UBSan propagates the runtime up its rdep graph
def test_no_sanitizer_blocklist_on_rdep_with_ubsan():
    name = "no_sanitizer_blocklist_on_rdep_with_ubsan"
    requested_target_name = name + "_requested_target"
    dep_name = name + "_dep"
    cc_library_static(
        name = requested_target_name,
        srcs = ["foo.cpp"],
        deps = [dep_name],
        copts = sanitizer_blocklist_select,
        additional_compiler_inputs = [sanitizer_blocklist_name],
        tags = ["manual"],
    )
    cc_library_shared(
        name = dep_name,
        srcs = ["bar.cpp"],
        features = ["ubsan_integer"],
        tags = ["manual"],
    )

    test_name = name + "_test"
    sanitizer_blocklist_test(
        name = test_name,
        target_under_test = requested_target_name,
        targets_without_flag = [requested_target_name + static_cpp_suffix],
        flags = [sanitizer_blocklist_flag],
    )

    return test_name

def test_sanitizer_blocklist_multiple_deps():
    name = "sanitizer_blocklist_multiple_deps"
    requested_target_name = name + "_requested_target"
    dep_name = name + "_dep"
    dep_of_dep_name = name + "_dep_of_dep"
    other_dep_name = name + "_other_dep"
    cc_library_shared(
        name = requested_target_name,
        srcs = ["foo.cpp"],
        implementation_deps = [dep_name, other_dep_name],
        features = ["android_cfi"],
        tags = ["manual"],
    )

    cc_library_static(
        name = dep_name,
        srcs = ["bar.cpp"],
        implementation_dynamic_deps = [dep_of_dep_name],
        features = ["android_cfi"],
        copts = sanitizer_blocklist_select,
        additional_compiler_inputs = [sanitizer_blocklist_name],
        tags = ["manual"],
    )

    cc_library_shared(
        name = other_dep_name,
        srcs = ["blah.cpp"],
        implementation_dynamic_deps = [dep_of_dep_name],
        tags = ["manual"],
    )

    cc_library_shared(
        name = dep_of_dep_name,
        srcs = ["baz.cpp"],
        features = ["android_cfi"],
        tags = ["manual"],
    )

    test_name = name + "_test"
    sanitizer_blocklist_test(
        name = test_name,
        target_under_test = requested_target_name,
        targets_with_flag = [dep_name + static_cpp_suffix],
        flags = [sanitizer_blocklist_flag],
    )

    return test_name

def sanitizer_blocklist_test_suite(name):
    native.test_suite(
        name = name,
        tests = [
            test_sanitizer_blocklist_with_ubsan_static(),
            test_sanitizer_blocklist_with_cfi_static(),
            test_no_sanitizer_blocklist_without_sanitizer_static(),
            test_sanitizer_blocklist_with_ubsan_shared(),
            test_sanitizer_blocklist_with_cfi_shared(),
            test_no_sanitizer_blocklist_without_sanitizer_shared(),
            test_sanitizer_blocklist_with_ubsan_binary(),
            test_sanitizer_blocklist_with_cfi_binary(),
            test_no_sanitizer_blocklist_without_sanitizer_binary(),
            test_sanitizer_blocklist_on_dep_with_cfi(),
            test_no_sanitizer_blocklist_on_dep_without_cfi(),
            test_no_sanitizer_blocklist_on_rdep_with_ubsan(),
            test_sanitizer_blocklist_multiple_deps(),
        ],
    )
