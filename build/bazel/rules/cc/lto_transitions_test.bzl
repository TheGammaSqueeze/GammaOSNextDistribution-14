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
load("//build/bazel/rules/cc:cc_binary.bzl", "cc_binary")
load("//build/bazel/rules/cc:cc_library_shared.bzl", "cc_library_shared")
load("//build/bazel/rules/cc:cc_library_static.bzl", "cc_library_static")
load(
    "//build/bazel/rules/cc/testing:transitions.bzl",
    "ActionArgsInfo",
    "compile_action_argv_aspect_generator",
)

lto_flag = "-flto=thin"
static_cpp_suffix = "_cpp"
shared_cpp_suffix = "__internal_root_cpp"
binary_suffix = "__internal_root"

def _lto_deps_test_impl(ctx):
    env = analysistest.begin(ctx)
    target_under_test = analysistest.target_under_test(env)
    argv_map = target_under_test[ActionArgsInfo].argv_map

    for target in ctx.attr.targets_with_lto:
        asserts.true(
            env,
            target in argv_map,
            "can't find {} in argv map".format(target),
        )
        if target in argv_map:
            argv = argv_map[target]
            asserts.true(
                env,
                lto_flag in argv,
                "Compile action of {} didn't have LTO but it was expected".format(
                    target,
                ),
            )
    for target in ctx.attr.targets_without_lto:
        asserts.true(
            env,
            target in argv_map,
            "can't find {} in argv map".format(target),
        )
        if target in argv_map:
            argv = argv_map[target]
            asserts.true(
                env,
                lto_flag not in argv,
                "Compile action of {} had LTO but it wasn't expected".format(
                    target,
                ),
            )
    return analysistest.end(env)

_compile_action_argv_aspect = compile_action_argv_aspect_generator({
    "_cc_library_combiner": ["deps", "roots", "includes"],
    "_cc_includes": ["deps"],
    "_cc_library_shared_proxy": ["deps"],
    "stripped_binary": ["androidmk_deps"],
})

lto_deps_test = analysistest.make(
    _lto_deps_test_impl,
    attrs = {
        "targets_with_lto": attr.string_list(),
        "targets_without_lto": attr.string_list(),
    },
    # We need to use aspect to examine the dependencies' actions of the apex
    # target as the result of the transition, checking the dependencies directly
    # using names will give you the info before the transition takes effect.
    extra_target_under_test_aspects = [_compile_action_argv_aspect],
)

def _test_static_deps_have_lto():
    name = "static_deps_have_lto"
    requested_target_name = name + "_requested_target"
    static_dep_name = name + "_static_dep"
    static_dep_of_static_dep_name = "_static_dep_of_static_dep"
    test_name = name + "_test"

    cc_library_static(
        name = requested_target_name,
        srcs = ["foo.cpp"],
        deps = [static_dep_name],
        features = ["android_thin_lto"],
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

    lto_deps_test(
        name = test_name,
        target_under_test = requested_target_name,
        targets_with_lto = [
            requested_target_name + static_cpp_suffix,
            static_dep_name + static_cpp_suffix,
            static_dep_of_static_dep_name + static_cpp_suffix,
        ],
        targets_without_lto = [],
    )

    return test_name

def _test_deps_of_shared_have_lto_if_enabled():
    name = "deps_of_shared_have_lto_if_enabled"
    requested_target_name = name + "_requested_target"
    shared_dep_name = name + "_shared_dep"
    static_dep_of_shared_dep_name = name + "_static_dep_of_shared_dep"
    test_name = name + "_test"

    cc_library_static(
        name = requested_target_name,
        srcs = ["foo.cpp"],
        dynamic_deps = [shared_dep_name],
        tags = ["manual"],
    )

    cc_library_shared(
        name = shared_dep_name,
        srcs = ["bar.cpp"],
        deps = [static_dep_of_shared_dep_name],
        features = ["android_thin_lto"],
        tags = ["manual"],
    )

    cc_library_static(
        name = static_dep_of_shared_dep_name,
        srcs = ["baz.cpp"],
        tags = ["manual"],
    )

    lto_deps_test(
        name = test_name,
        target_under_test = requested_target_name,
        targets_with_lto = [
            shared_dep_name + "__internal_root_cpp",
            static_dep_of_shared_dep_name + static_cpp_suffix,
        ],
        targets_without_lto = [requested_target_name + static_cpp_suffix],
    )

    return test_name

def _test_deps_of_shared_deps_no_lto_if_disabled():
    name = "deps_of_shared_deps_no_lto_if_disabled"
    requested_target_name = name + "_requested_target"
    shared_dep_name = name + "_shared_dep"
    static_dep_of_shared_dep_name = name + "_static_dep_of_shared_dep"
    test_name = name + "_test"

    cc_library_static(
        name = requested_target_name,
        srcs = ["foo.cpp"],
        dynamic_deps = [shared_dep_name],
        features = ["android_thin_lto"],
        tags = ["manual"],
    )

    cc_library_shared(
        name = shared_dep_name,
        srcs = ["bar.cpp"],
        deps = [static_dep_of_shared_dep_name],
        tags = ["manual"],
    )

    cc_library_static(
        name = static_dep_of_shared_dep_name,
        srcs = ["baz.cpp"],
        tags = ["manual"],
    )

    lto_deps_test(
        name = test_name,
        target_under_test = requested_target_name,
        targets_with_lto = [requested_target_name + static_cpp_suffix],
        targets_without_lto = [
            shared_dep_name + shared_cpp_suffix,
            static_dep_of_shared_dep_name + static_cpp_suffix,
        ],
    )

    return test_name

def _test_binary_propagates_to_static_deps():
    name = "binary_propagates_to_static_deps"
    requested_target_name = name + "_requested_target"
    dep_name = name + "_dep"
    test_name = name + "_test"

    cc_binary(
        name = requested_target_name,
        srcs = ["foo.cpp"],
        deps = [dep_name],
        features = ["android_thin_lto"],
        tags = ["manual"],
    )

    cc_library_static(
        name = dep_name,
        srcs = ["bar.cpp"],
        tags = ["manual"],
    )

    lto_deps_test(
        name = test_name,
        target_under_test = requested_target_name,
        targets_with_lto = [
            requested_target_name + binary_suffix + static_cpp_suffix,
            dep_name + static_cpp_suffix,
        ],
    )

    return test_name

def lto_transition_test_suite(name):
    native.test_suite(
        name = name,
        tests = [
            _test_static_deps_have_lto(),
            _test_deps_of_shared_have_lto_if_enabled(),
            _test_deps_of_shared_deps_no_lto_if_disabled(),
            _test_binary_propagates_to_static_deps(),
        ],
    )
