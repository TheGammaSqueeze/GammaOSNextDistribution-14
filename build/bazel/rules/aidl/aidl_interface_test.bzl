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

load("@bazel_skylib//lib:new_sets.bzl", "sets")
load("@bazel_skylib//lib:paths.bzl", "paths")
load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts")
load("//build/bazel/rules/aidl:aidl_interface.bzl", "aidl_interface")
load("//build/bazel/rules/aidl:aidl_library.bzl", "AidlGenInfo")
load("//build/bazel/rules/test_common:rules.bzl", "target_under_test_exist_test")
load("//build/bazel/rules/test_common:flags.bzl", "action_flags_present_only_for_mnemonic_test_with_config_settings")

def _ndk_backend_test_impl(ctx):
    env = analysistest.begin(ctx)
    actions = analysistest.target_actions(env)
    asserts.true(
        env,
        len(actions) == 1,
        "expected to have one action per aidl_library target",
    )
    cc_aidl_code_gen_target = analysistest.target_under_test(env)

    # output_path: <bazel-bin>/<package-dir>/<cc_aidl_library-labelname>_aidl_code_gen
    # Since cc_aidl_library-label is unique among cpp and ndk backends,
    # the output_path is guaranteed to be unique
    output_path = paths.join(
        ctx.genfiles_dir.path,
        ctx.label.package,
        cc_aidl_code_gen_target.label.name,
    )
    expected_outputs = [
        # headers for ndk backend are nested in aidl directory to prevent
        # collision in c++ namespaces with cpp backend
        paths.join(output_path, "aidl/b/BpFoo.h"),
        paths.join(output_path, "aidl/b/BnFoo.h"),
        paths.join(output_path, "aidl/b/Foo.h"),
        paths.join(output_path, "b/Foo.cpp"),
    ]

    # Check output files in DefaultInfo provider
    asserts.set_equals(
        env,
        sets.make(expected_outputs),
        sets.make([
            output.path
            for output in cc_aidl_code_gen_target[DefaultInfo].files.to_list()
        ]),
    )

    # Check the output path is correctly added to includes in CcInfo.compilation_context
    asserts.true(
        env,
        output_path in cc_aidl_code_gen_target[CcInfo].compilation_context.includes.to_list(),
        "output path is added to CcInfo.compilation_context.includes",
    )

    return analysistest.end(env)

ndk_backend_test = analysistest.make(
    _ndk_backend_test_impl,
)

def _ndk_backend_test():
    name = "foo"
    aidl_library_target = name + "-ndk"
    aidl_code_gen_target = aidl_library_target + "_aidl_code_gen"
    test_name = aidl_code_gen_target + "_test"

    aidl_interface(
        name = "foo",
        ndk_config = {
            "enabled": True,
        },
        unstable = True,
        srcs = ["a/b/Foo.aidl"],
        strip_import_prefix = "a",
        tags = ["manual"],
    )

    ndk_backend_test(
        name = test_name,
        target_under_test = aidl_code_gen_target,
    )

    return test_name

def _ndk_config_test_impl(ctx):
    env = analysistest.begin(ctx)
    actions = analysistest.target_actions(env)
    asserts.true(
        env,
        len(actions) == 1,
        "expected to have one action per aidl_library target",
    )
    asserts.true(
        env,
        "--min_sdk_version=30",
        "expected to have min_sdk_version flag",
    )
    return analysistest.end(env)

ndk_config_test = analysistest.make(
    _ndk_config_test_impl,
)

def _ndk_config_test():
    name = "ndk-config"
    aidl_library_target = name + "-ndk"
    aidl_code_gen_target = aidl_library_target + "_aidl_code_gen"
    test_name = aidl_code_gen_target + "_test"

    aidl_interface(
        name = name,
        unstable = True,
        ndk_config = {
            "enabled": True,
            "min_sdk_version": "30",
        },
        srcs = ["Foo.aidl"],
        tags = ["manual"],
    )

    ndk_config_test(
        name = test_name,
        target_under_test = aidl_code_gen_target,
    )

    return test_name

def _aidl_library_has_flags_test_impl(ctx):
    env = analysistest.begin(ctx)
    target_under_test = analysistest.target_under_test(env)

    asserts.true(
        env,
        AidlGenInfo in target_under_test,
        "",
    )
    asserts.equals(
        env,
        ctx.attr.expected_flags,
        target_under_test[AidlGenInfo].flags,
        "",
    )

    return analysistest.end(env)

aidl_library_has_flags_test = analysistest.make(
    _aidl_library_has_flags_test_impl,
    attrs = {
        "expected_flags": attr.string_list(),
    },
)

def _test_aidl_interface_passes_flags_to_aidl_libraries():
    name = "aidl_interface_passes_version_flags_to_aidl_libraries"
    aidl_interface(
        name = name,
        srcs = ["Foo.aidl"],
        tags = ["manual"],
        versions_with_info = [
            {
                "version": "1",
            },
            {
                "version": "2",
            },
        ],
    )

    target_v1_test_name = name + "_test-V1"
    aidl_library_has_flags_test(
        name = target_v1_test_name,
        target_under_test = name + "-V1",
        expected_flags = [
            "--structured",
            "--version=1",
        ],
    )
    target_v2_test_name = name + "_test-V2"
    aidl_library_has_flags_test(
        name = target_v2_test_name,
        target_under_test = name + "-V2",
        expected_flags = [
            "--structured",
            "--version=2",
        ],
    )
    target_v_next_test_name = name + "_test-V_next"
    aidl_library_has_flags_test(
        name = target_v_next_test_name,
        target_under_test = name + "-V3",
        expected_flags = [
            "--structured",
            "--version=3",
        ],
    )

    return [
        target_v1_test_name,
        target_v2_test_name,
        target_v_next_test_name,
    ]

def _next_version_for_unversioned_stable_interface_test():
    name = "unversioned_stable_interface_next_version"
    test_name = name + "_test"
    next_version_aidl_library_target = name + "-V1"

    aidl_interface(
        name = name,
        srcs = ["Foo.aidl"],
        tags = ["manual"],
    )

    target_under_test_exist_test(
        name = test_name,
        target_under_test = next_version_aidl_library_target,
    )

    return test_name

def _next_version_for_versioned_stable_interface_test():
    name = "versioned_stable_interface_next_version"
    test_name = name + "_test"
    next_version_aidl_library_target = name + "-V3"

    aidl_interface(
        name = name,
        versions_with_info = [
            {
                "version": "1",
            },
            {
                "version": "2",
            },
        ],
        srcs = ["Foo.aidl"],
        tags = ["manual"],
    )

    target_under_test_exist_test(
        name = test_name,
        target_under_test = next_version_aidl_library_target,
    )

    return test_name

def _tidy_flags_has_generated_directory_header_filter_test_impl(ctx):
    env = analysistest.begin(ctx)

    actions = analysistest.target_actions(env)
    clang_tidy_actions = [a for a in actions if a.mnemonic == "ClangTidy"]

    action_header_filter = None
    for action in clang_tidy_actions:
        for arg in action.argv:
            if arg.startswith("-header-filter="):
                action_header_filter = arg

    if action_header_filter == None:
        asserts.true(
            env,
            False,
            "did not find header-filter in ClangTidy actions: `%s`" % (
                clang_tidy_actions
            ),
        )

    # The genfiles path for tests and cc_libraries is different (the latter contains a
    # configuration prefix ST-<hash>). So instead (lacking regexes) we can just check
    # that the beginning and end of the header-filter is correct.
    expected_header_filter_prefix = "-header-filter=" + paths.join(ctx.genfiles_dir.path)
    expected_header_filter_prefix = expected_header_filter_prefix.removesuffix("/bin")
    expected_header_filter_suffix = ctx.label.package + ".*"
    asserts.true(
        env,
        action_header_filter.startswith(expected_header_filter_prefix),
        "expected header-filter to start with `%s`; but was `%s`" % (
            expected_header_filter_prefix,
            action_header_filter,
        ),
    )
    asserts.true(
        env,
        action_header_filter.endswith(expected_header_filter_suffix),
        "expected header-filter to end with `%s`; but was `%s`" % (
            expected_header_filter_suffix,
            action_header_filter,
        ),
    )

    return analysistest.end(env)

_tidy_flags_has_generated_directory_header_filter_test = analysistest.make(
    _tidy_flags_has_generated_directory_header_filter_test_impl,
    config_settings = {
        "@//build/bazel/flags/cc/tidy:allow_local_tidy_true": True,
    },
)

def _test_aidl_interface_generated_header_filter():
    name = "aidl_interface_generated_header_filter"
    test_name = name + "_test"
    aidl_library_target = name + "-cpp"
    shared_target_under_test = aidl_library_target + "__internal_root"
    shared_test_name = test_name + "_shared"
    static_target_under_test = aidl_library_target + "_bp2build_cc_library_static"
    static_test_name = test_name + "_static"

    aidl_interface(
        name = name,
        cpp_config = {
            "enabled": True,
        },
        unstable = True,
        srcs = ["a/b/Foo.aidl"],
        tags = ["manual"],
    )

    _tidy_flags_has_generated_directory_header_filter_test(
        name = shared_test_name,
        target_under_test = shared_target_under_test,
    )
    _tidy_flags_has_generated_directory_header_filter_test(
        name = static_test_name,
        target_under_test = static_target_under_test,
    )
    return [
        shared_test_name,
        static_test_name,
    ]

_action_flags_present_with_tidy_test = action_flags_present_only_for_mnemonic_test_with_config_settings({
    "@//build/bazel/flags/cc/tidy:allow_local_tidy_true": True,
})

def _test_aidl_interface_generated_cc_library_has_correct_tidy_checks_as_errors():
    name = "aidl_interface_generated_cc_library_has_correct_tidy_checks_as_errors"
    test_name = name + "_test"
    aidl_library_target = name + "-cpp"
    shared_target_under_test = aidl_library_target + "__internal_root"
    shared_test_name = test_name + "_shared"
    static_target_under_test = aidl_library_target + "_bp2build_cc_library_static"
    static_test_name = test_name + "_static"

    aidl_interface(
        name = name,
        cpp_config = {
            "enabled": True,
        },
        unstable = True,
        srcs = ["a/b/Foo.aidl"],
        tags = ["manual"],
    )

    _action_flags_present_with_tidy_test(
        name = shared_test_name,
        target_under_test = shared_target_under_test,
        mnemonics = ["ClangTidy"],
        expected_flags = [
            "-warnings-as-errors=*,-clang-analyzer-deadcode.DeadStores,-clang-analyzer-cplusplus.NewDeleteLeaks,-clang-analyzer-optin.performance.Padding,-bugprone-assignment-in-if-condition,-bugprone-branch-clone,-bugprone-signed-char-misuse,-misc-const-correctness,-bugprone-unsafe-functions,-cert-msc24-c,-cert-msc33-c,-modernize-type-traits,-readability-avoid-unconditional-preprocessor-if",
        ],
    )
    _action_flags_present_with_tidy_test(
        name = static_test_name,
        target_under_test = static_target_under_test,
        mnemonics = ["ClangTidy"],
        expected_flags = [
            "-warnings-as-errors=*,-clang-analyzer-deadcode.DeadStores,-clang-analyzer-cplusplus.NewDeleteLeaks,-clang-analyzer-optin.performance.Padding,-bugprone-assignment-in-if-condition,-bugprone-branch-clone,-bugprone-signed-char-misuse,-misc-const-correctness,-bugprone-unsafe-functions,-cert-msc24-c,-cert-msc33-c,-modernize-type-traits,-readability-avoid-unconditional-preprocessor-if",
        ],
    )
    return [
        shared_test_name,
        static_test_name,
    ]

def _cc_library_has_flags_test_impl(ctx):
    env = analysistest.begin(ctx)
    target = analysistest.target_under_test(env)
    actions = [a for a in target.actions if a.mnemonic == "CppCompile"]

    asserts.true(
        env,
        len(actions) == 1,
        "There should be one cc compile action: %s" % actions,
    )

    action = actions[0]
    for flag in ctx.attr.expected_flags:
        if flag not in action.argv:
            fail("{} is not in list of flags for linking {}".format(flag, action.argv))

    return analysistest.end(env)

cc_library_has_flags_test = analysistest.make(
    _cc_library_has_flags_test_impl,
    attrs = {
        "expected_flags": attr.string_list(),
    },
)

def _test_aidl_interface_sets_flags_to_cc_libraries():
    name = "aidl_interface_sets_flags_to_cc_libraries"
    test_name = name + "_test"
    aidl_library_target = name + "-ndk"
    shared_target_under_test = aidl_library_target + "__internal_root_cpp"
    shared_test_name = test_name + "_shared"
    static_target_under_test = aidl_library_target + "_bp2build_cc_library_static_cpp"
    static_test_name = test_name + "_static"

    aidl_interface(
        name = name,
        ndk_config = {
            "enabled": True,
        },
        srcs = ["Foo.aidl"],
        unstable = True,
        tags = ["manual"],
    )

    cc_library_has_flags_test(
        name = shared_test_name,
        target_under_test = shared_target_under_test,
        expected_flags = [
            "-DBINDER_STABILITY_SUPPORT",
        ],
    )

    cc_library_has_flags_test(
        name = static_test_name,
        target_under_test = static_target_under_test,
        expected_flags = [
            "-DBINDER_STABILITY_SUPPORT",
        ],
    )

    return [
        shared_test_name,
        static_test_name,
    ]

def aidl_interface_test_suite(name):
    native.test_suite(
        name = name,
        tests = (
            [
                "//build/bazel/rules/aidl/testing:generated_targets_have_correct_srcs_test",
                "//build/bazel/rules/aidl/testing:interface_macro_produces_all_targets_test",
                _ndk_backend_test(),
                _ndk_config_test(),
                _next_version_for_unversioned_stable_interface_test(),
                _next_version_for_versioned_stable_interface_test(),
            ] +
            _test_aidl_interface_generated_header_filter() +
            _test_aidl_interface_passes_flags_to_aidl_libraries() +
            _test_aidl_interface_sets_flags_to_cc_libraries() +
            _test_aidl_interface_generated_cc_library_has_correct_tidy_checks_as_errors()
        ),
    )
