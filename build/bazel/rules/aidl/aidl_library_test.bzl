"""Copyright (C) 2022 The Android Open Source Project

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

load("@bazel_skylib//lib:paths.bzl", "paths")
load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts")
load(":aidl_library.bzl", "AidlGenInfo", "aidl_library")

PACKAGE_ROOT = "build/bazel/rules/aidl"

def _test_include_dirs_are_transitive_impl(ctx):
    env = analysistest.begin(ctx)
    target_under_test = analysistest.target_under_test(env)

    asserts.equals(
        env,
        expected = [
            # direct include dir is the first in the list returned from
            # transitive_include_dirs.to_list() because transitive_include_dir
            # is created with preorder
            # TODO(b/243825300): Move direct include_dir out of transitive_include_dir
            # so that we don't have to rely on preorder traversal
            paths.join(ctx.genfiles_dir.path, PACKAGE_ROOT, "_virtual_imports", "include_dirs_transitivity"),
            paths.join(ctx.genfiles_dir.path, PACKAGE_ROOT, "_virtual_imports", "include_dirs_transitivity_dependency"),
        ],
        actual = target_under_test[AidlGenInfo].transitive_include_dirs.to_list(),
    )

    return analysistest.end(env)

include_dirs_are_transitive_test = analysistest.make(_test_include_dirs_are_transitive_impl)

def _test_include_dirs_transitivity():
    test_base_name = "include_dirs_transitivity"
    test_name = test_base_name + "_test"
    aidl_dep = test_base_name + "_dependency"
    aidl_library(
        name = test_base_name,
        strip_import_prefix = "testing",
        deps = [":" + aidl_dep],
        tags = ["manual"],
    )
    aidl_library(
        name = aidl_dep,
        strip_import_prefix = "testing2",
        tags = ["manual"],
    )
    include_dirs_are_transitive_test(
        name = test_name,
        target_under_test = test_base_name,
    )
    return [
        test_name,
    ]

def _test_empty_srcs_aggregates_deps_impl(ctx):
    env = analysistest.begin(ctx)
    target_under_test = analysistest.target_under_test(env)

    asserts.equals(
        env,
        expected = [],
        actual = target_under_test[AidlGenInfo].srcs.to_list(),
    )

    import_path = paths.join(
        PACKAGE_ROOT,
        "_virtual_imports",
    )

    asserts.equals(
        env,
        expected = [
            paths.join(import_path, target_under_test.label.name + "_dependency2", "b.aidl"),
            paths.join(import_path, target_under_test.label.name + "_dependency2", "header_b.aidl"),
            paths.join(import_path, target_under_test.label.name + "_dependency3", "c.aidl"),
            paths.join(import_path, target_under_test.label.name + "_dependency3", "header_c.aidl"),
            paths.join(import_path, target_under_test.label.name + "_dependency1", "a.aidl"),
            paths.join(import_path, target_under_test.label.name + "_dependency1", "header_a.aidl"),
        ],
        actual = [
            file.short_path
            for file in target_under_test[AidlGenInfo].transitive_srcs.to_list()
        ],
    )

    return analysistest.end(env)

empty_srcs_aggregates_deps_test = analysistest.make(_test_empty_srcs_aggregates_deps_impl)

def _test_hdrs_are_only_in_transitive_srcs_impl(ctx):
    env = analysistest.begin(ctx)
    target_under_test = analysistest.target_under_test(env)

    import_path = paths.join(
        PACKAGE_ROOT,
        "_virtual_imports",
        target_under_test.label.name,
    )

    asserts.equals(
        env,
        expected = [
            paths.join(import_path, "direct.aidl"),
        ],
        actual = [
            file.short_path
            for file in target_under_test[AidlGenInfo].srcs.to_list()
        ],
    )

    asserts.equals(
        env,
        expected = [
            paths.join(import_path, "header_direct.aidl"),
        ],
        actual = [
            file.short_path
            for file in target_under_test[AidlGenInfo].hdrs.to_list()
        ],
    )

    return analysistest.end(env)

hdrs_are_only_in_transitive_srcs_test = analysistest.make(_test_hdrs_are_only_in_transitive_srcs_impl)

def _test_transitive_srcs_contains_direct_and_transitive_srcs_impl(ctx):
    env = analysistest.begin(ctx)
    target_under_test = analysistest.target_under_test(env)

    import_path = paths.join(
        PACKAGE_ROOT,
        "_virtual_imports",
    )

    asserts.equals(
        env,
        expected = [
            paths.join(import_path, target_under_test.label.name, "direct.aidl"),
        ],
        actual = [
            file.short_path
            for file in target_under_test[AidlGenInfo].srcs.to_list()
        ],
    )

    asserts.equals(
        env,
        expected = [
            paths.join(import_path, target_under_test.label.name + "_dependency2", "b.aidl"),
            paths.join(import_path, target_under_test.label.name + "_dependency2", "header_b.aidl"),
            paths.join(import_path, target_under_test.label.name + "_dependency3", "c.aidl"),
            paths.join(import_path, target_under_test.label.name + "_dependency3", "header_c.aidl"),
            paths.join(import_path, target_under_test.label.name + "_dependency1", "a.aidl"),
            paths.join(import_path, target_under_test.label.name + "_dependency1", "header_a.aidl"),
            paths.join(import_path, target_under_test.label.name, "direct.aidl"),
            paths.join(import_path, target_under_test.label.name, "header_direct.aidl"),
        ],
        actual = [
            file.short_path
            for file in target_under_test[AidlGenInfo].transitive_srcs.to_list()
        ],
    )

    return analysistest.end(env)

transitive_srcs_contains_direct_and_transitive_srcs_test = analysistest.make(
    _test_transitive_srcs_contains_direct_and_transitive_srcs_impl,
)

def _generate_test_targets(name):
    aidl_dep1 = name + "_dependency1"
    aidl_dep2 = name + "_dependency2"
    aidl_dep3 = name + "_dependency3"
    aidl_library(
        name = aidl_dep1,
        srcs = ["a.aidl"],
        hdrs = ["header_a.aidl"],
        deps = [
            ":" + aidl_dep2,
            ":" + aidl_dep3,
        ],
        tags = ["manual"],
    )
    aidl_library(
        name = aidl_dep2,
        srcs = ["b.aidl"],
        hdrs = ["header_b.aidl"],
        tags = ["manual"],
    )
    aidl_library(
        name = aidl_dep3,
        srcs = ["c.aidl"],
        hdrs = ["header_c.aidl"],
        tags = ["manual"],
    )
    return aidl_dep1

def _test_empty_srcs_aggregates_deps():
    test_base_name = "empty_srcs_aggregates_deps"
    test_name = test_base_name + "_test"

    aidl_dep1 = _generate_test_targets(test_base_name)
    aidl_library(
        name = test_base_name,
        deps = [":" + aidl_dep1],
        tags = ["manual"],
    )
    empty_srcs_aggregates_deps_test(
        name = test_name,
        target_under_test = test_base_name,
    )
    return [
        test_name,
    ]

def _test_transitive_srcs_contains_direct_and_transitive_srcs():
    test_base_name = "transitive_srcs_contains_direct_and_transitive_srcs"
    srcs_test_name = test_base_name + "_srcs"
    hdrs_test_name = test_base_name + "_hdrs"

    aidl_dep1 = _generate_test_targets(test_base_name)
    aidl_library(
        name = test_base_name,
        srcs = ["direct.aidl"],
        hdrs = ["header_direct.aidl"],
        deps = [":" + aidl_dep1],
        tags = ["manual"],
    )
    transitive_srcs_contains_direct_and_transitive_srcs_test(
        name = srcs_test_name,
        target_under_test = test_base_name,
    )
    hdrs_are_only_in_transitive_srcs_test(
        name = hdrs_test_name,
        target_under_test = test_base_name,
    )
    return [
        srcs_test_name,
        hdrs_test_name,
    ]

def aidl_library_test_suite(name):
    native.test_suite(
        name = name,
        tests = _test_include_dirs_transitivity() +
                _test_transitive_srcs_contains_direct_and_transitive_srcs() +
                _test_empty_srcs_aggregates_deps(),
    )
