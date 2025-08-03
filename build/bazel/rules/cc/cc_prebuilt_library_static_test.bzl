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
load("//build/bazel/rules/cc:cc_prebuilt_library_static.bzl", "cc_prebuilt_library_static")
load("//build/bazel/rules/test_common:paths.bzl", "get_output_and_package_dir_based_path")

_fake_expected_lib = "{[()]}"

def _cc_prebuilt_library_static_alwayslink_test_impl(ctx):
    env = analysistest.begin(ctx)
    target = analysistest.target_under_test(env)
    expected_lib = ctx.attr.expected_lib
    alwayslink = ctx.attr.alwayslink
    cc_info = target[CcInfo]
    linker_inputs = cc_info.linking_context.linker_inputs.to_list()
    libs_to_link = []
    for l in linker_inputs:
        libs_to_link += l.libraries

    has_lib = False
    has_alwayslink = False
    libs = {}
    for lib_to_link in libs_to_link:
        lib = lib_to_link.static_library.basename
        libs[lib_to_link.static_library] = lib_to_link.alwayslink
        if lib == expected_lib:
            has_lib = True
            has_alwayslink = lib_to_link.alwayslink
        if has_alwayslink:
            break
    asserts.true(
        env,
        has_lib,
        "\nExpected to find the static library `%s` in the linker_input:\n\t%s" % (expected_lib, str(libs)),
    )
    asserts.equals(
        env,
        has_alwayslink,
        alwayslink,
        "\nExpected to find the static library `%s` unconditionally in the linker_input, with alwayslink set to %s:\n\t%s" % (expected_lib, alwayslink, str(libs)),
    )

    return analysistest.end(env)

_cc_prebuilt_library_static_alwayslink_test = analysistest.make(
    _cc_prebuilt_library_static_alwayslink_test_impl,
    attrs = {
        "expected_lib": attr.string(),
        "alwayslink": attr.bool(),
    },
)

def _cc_prebuilt_library_static_alwayslink_lib(alwayslink):
    name = "_cc_prebuilt_library_static_alwayslink_lib_" + str(alwayslink)
    test_name = name + "_test"
    lib = "libfoo.a"

    cc_prebuilt_library_static(
        name = name,
        static_library = lib,
        alwayslink = alwayslink,
        tags = ["manual"],
    )
    _cc_prebuilt_library_static_alwayslink_test(
        name = test_name,
        target_under_test = name,
        expected_lib = lib,
        alwayslink = alwayslink,
    )

    return test_name

def _cc_prebuilt_library_static_test_impl(ctx):
    env = analysistest.begin(ctx)
    target = analysistest.target_under_test(env)
    expected_lib = ctx.attr.expected_lib
    cc_info = target[CcInfo]
    compilation_context = cc_info.compilation_context
    linker_inputs = cc_info.linking_context.linker_inputs.to_list()
    libs_to_link = []
    for lib in linker_inputs:
        libs_to_link += lib.libraries

    if expected_lib == _fake_expected_lib:
        asserts.true(
            env,
            len(libs_to_link) == 0,
            "\nExpected the static library to be empty, but instead got:\n\t%s\n" % str(libs_to_link),
        )
    else:
        asserts.true(
            env,
            expected_lib in [lib.static_library.basename for lib in libs_to_link],
            "\nExpected the target to include the static library %s; but instead got:\n\t%s\n" % (expected_lib, libs_to_link),
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

_cc_prebuilt_library_static_test = analysistest.make(
    _cc_prebuilt_library_static_test_impl,
    attrs = dict(
        expected_lib = attr.string(default = _fake_expected_lib),
        expected_includes = attr.string_list(),
        expected_system_includes = attr.string_list(),
    ),
)

def _cc_prebuilt_library_static_simple():
    name = "_cc_prebuilt_library_static_simple"
    test_name = name + "_test"
    lib = "libfoo.a"

    cc_prebuilt_library_static(
        name = name,
        static_library = lib,
        tags = ["manual"],
    )
    _cc_prebuilt_library_static_test(
        name = test_name,
        target_under_test = name,
        expected_lib = lib,
    )

    return test_name

def _cc_prebuilt_library_static_None():
    name = "_cc_prebuilt_library_static_None"
    test_name = name + "_test"
    lib = None

    cc_prebuilt_library_static(
        name = name,
        static_library = lib,
        tags = ["manual"],
    )
    _cc_prebuilt_library_static_test(
        name = test_name,
        target_under_test = name,
        expected_lib = lib,  # We expect the default of _fake_expected_lib
    )

    return test_name

def _cc_prebuilt_library_static_has_all_includes():
    name = "_cc_prebuilt_library_static_has_all_includes"
    test_name = name + "_test"
    lib = "libfoo.a"
    includes = ["includes"]
    system_includes = ["system_includes"]

    cc_prebuilt_library_static(
        name = name,
        static_library = lib,
        export_includes = includes,
        export_system_includes = system_includes,
        tags = ["manual"],
    )
    _cc_prebuilt_library_static_test(
        name = test_name,
        target_under_test = name,
        expected_lib = lib,
        expected_includes = includes,
        expected_system_includes = system_includes,
    )

    return test_name

# TODO: Test that is alwayslink = alse

def cc_prebuilt_library_static_test_suite(name):
    native.test_suite(
        name = name,
        tests = [
            _cc_prebuilt_library_static_simple(),
            _cc_prebuilt_library_static_None(),
            _cc_prebuilt_library_static_alwayslink_lib(True),
            _cc_prebuilt_library_static_alwayslink_lib(False),
            _cc_prebuilt_library_static_has_all_includes(),
        ],
    )
