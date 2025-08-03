"""
Copyright (C) 2023 The Android Open Source Project

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

load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts")
load("//build/bazel/rules/cc:cc_prebuilt_library_shared.bzl", "cc_prebuilt_library_shared")
load("//build/bazel/rules/test_common:paths.bzl", "get_output_and_package_dir_based_path")
load(":cc_binary_test.bzl", "strip_test_assert_flags")

def _cc_prebuilt_library_shared_test_impl(ctx):
    env = analysistest.begin(ctx)
    target = analysistest.target_under_test(env)
    expected_lib = ctx.attr.expected_lib
    cc_info = target[CcInfo]
    compilation_context = cc_info.compilation_context
    linker_inputs = cc_info.linking_context.linker_inputs.to_list()
    libs_to_link = []
    for lib in linker_inputs:
        libs_to_link += lib.libraries

    asserts.true(
        env,
        expected_lib in [lib.dynamic_library.basename for lib in libs_to_link],
        "\nExpected the target to include the shared library %s; but instead got:\n\t%s\n" % (expected_lib, libs_to_link),
    )

    actions = analysistest.target_actions(env)
    strip_acts = [a for a in actions if a.mnemonic == "SolibSymlink"]
    has_strip = len(strip_acts) > 0
    asserts.true(
        env,
        has_strip,
        "expected to find an action with CcStrip mnemonic in:\n%s" % actions,
    )

    # Checking for the expected {,system_}includes
    assert_template = "\nExpected the %s for " + expected_lib + " to be:\n\t%s\n, but instead got:\n\t%s\n"
    expand_paths = lambda paths: [get_output_and_package_dir_based_path(env, p) for p in paths]
    expected_includes = expand_paths(ctx.attr.expected_includes)
    expected_system_includes = expand_paths(ctx.attr.expected_system_includes)

    includes = compilation_context.includes.to_list()
    for include in expected_includes:
        asserts.true(env, include in includes, assert_template % ("includes", expected_includes, includes))

    system_includes = compilation_context.system_includes.to_list()
    for include in expected_system_includes:
        asserts.true(env, include in system_includes, assert_template % ("system_includes", expected_system_includes, system_includes))

    return analysistest.end(env)

_cc_prebuilt_library_shared_test = analysistest.make(
    _cc_prebuilt_library_shared_test_impl,
    attrs = dict(
        expected_lib = attr.string(),
        expected_includes = attr.string_list(),
        expected_system_includes = attr.string_list(),
    ),
)

def _cc_prebuilt_library_shared_simple():
    name = "_cc_prebuilt_library_shared_simple"
    test_name = name + "_test"
    lib = name + ".so"

    cc_prebuilt_library_shared(
        name = name,
        shared_library = lib,
        tags = ["manual"],
    )
    _cc_prebuilt_library_shared_test(
        name = test_name,
        target_under_test = name,
        expected_lib = lib,
    )

    return test_name

def _cc_prebuilt_library_shared_has_all_includes():
    name = "_cc_prebuilt_library_shared_has_all_includes"
    test_name = name + "_test"
    lib = name + ".so"
    includes = ["includes"]
    system_includes = ["system_includes"]

    cc_prebuilt_library_shared(
        name = name,
        shared_library = lib,
        export_includes = includes,
        export_system_includes = system_includes,
        tags = ["manual"],
    )
    _cc_prebuilt_library_shared_test(
        name = test_name,
        target_under_test = name,
        expected_lib = lib,
        expected_includes = includes,
        expected_system_includes = system_includes,
    )

    return test_name

def _cc_prebuilt_library_shared_stripped_test_impl(ctx):
    env = analysistest.begin(ctx)
    actions = analysistest.target_actions(env)
    strip_acts = [a for a in actions if a.mnemonic == "CcStrip"]
    has_strip = len(strip_acts) > 0
    asserts.true(
        env,
        has_strip,
        "expected to find an action with CcStrip mnemonic in:\n%s" % actions,
    )
    if has_strip:
        strip_test_assert_flags(env, strip_acts[0], ctx.attr.strip_flags)
    return analysistest.end(env)

_cc_prebuilt_library_shared_stripped_test = analysistest.make(
    _cc_prebuilt_library_shared_stripped_test_impl,
    attrs = dict(
        strip_flags = attr.string_list(),
    ),
)

def _cc_prebuilt_library_shared_stripped_all():
    name = "_cc_prebuilt_library_shared_stripped_all"
    test_name = name + "_test"

    cc_prebuilt_library_shared(
        name = name,
        shared_library = "foo.so",
        all = True,
        tags = ["manual"],
    )
    _cc_prebuilt_library_shared_stripped_test(
        name = test_name,
        target_under_test = name,
        strip_flags = [
            "--add-gnu-debuglink",
        ],
    )
    return test_name

def _no_input_shared_library_succeeds_test_impl(ctx):
    env = analysistest.begin(ctx)
    target = analysistest.target_under_test(env)
    cc_info = target[CcInfo]
    linker_inputs = cc_info.linking_context.linker_inputs.to_list()
    libs_to_link = []
    for lib in linker_inputs:
        libs_to_link.extend(lib.libraries)
    asserts.equals(
        env,
        len(libs_to_link),
        0,
        "\nExpected the shared library to be empty, but instead got:\n\t%s\n" % str(libs_to_link),
    )

    actions = analysistest.target_actions(env)
    asserts.equals(
        env,
        len(actions),
        0,
        "expect no actions for no input",
    )

    return analysistest.end(env)

_no_input_shared_library_succeeds_test = analysistest.make(
    _no_input_shared_library_succeeds_test_impl,
)

def _cc_prebuilt_library_shared_no_input_succeeds():
    name = "_cc_prebuilt_library_shared_no_input_succeeds"
    test_name = name + "_test"

    cc_prebuilt_library_shared(
        name = name,
        tags = ["manual"],
    )
    _no_input_shared_library_succeeds_test(
        name = test_name,
        target_under_test = name,
    )

    return test_name

def cc_prebuilt_library_shared_test_suite(name):
    native.test_suite(
        name = name,
        tests = [
            _cc_prebuilt_library_shared_simple(),
            _cc_prebuilt_library_shared_has_all_includes(),
            _cc_prebuilt_library_shared_stripped_all(),
            _cc_prebuilt_library_shared_no_input_succeeds(),
        ],
    )
