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
load("//build/bazel/rules/cc:cc_library_headers.bzl", "cc_library_headers")
load("//build/bazel/rules/cc:cc_library_shared.bzl", "cc_library_shared")
load("//build/bazel/rules/cc:cc_library_static.bzl", "cc_library_static")
load("//build/bazel/rules/cc:cc_prebuilt_library_static.bzl", "cc_prebuilt_library_static")
load(
    "//build/bazel/rules/test_common:flags.bzl",
    "action_flags_absent_for_mnemonic_test",
    "action_flags_present_only_for_mnemonic_test",
)
load("//build/bazel/rules/test_common:paths.bzl", "get_output_and_package_dir_based_path", "get_package_dir_based_path")
load("//build/bazel/rules/test_common:rules.bzl", "expect_failure_test")
load(":cc_library_common_test.bzl", "target_provides_androidmk_info_test", "target_sdk_variant_provides_androidmk_info_test")

def _cc_library_static_propagating_compilation_context_test_impl(ctx):
    env = analysistest.begin(ctx)
    target = analysistest.target_under_test(env)
    cc_info = target[CcInfo]
    compilation_context = cc_info.compilation_context

    header_paths = [f.short_path for f in compilation_context.headers.to_list()]
    for hdr in ctx.files.expected_hdrs:
        asserts.true(
            env,
            hdr.short_path in header_paths,
            "Did not find {hdr} in includes: {hdrs}.".format(hdr = hdr, hdrs = compilation_context.headers),
        )

    for hdr in ctx.files.expected_absent_hdrs:
        asserts.true(
            env,
            hdr not in header_paths,
            "Found {hdr} in includes: {hdrs}, should not be present.".format(hdr = hdr, hdrs = compilation_context.headers),
        )

    for include in ctx.attr.expected_includes:
        absolute_include = get_package_dir_based_path(env, include)
        asserts.true(
            env,
            absolute_include in compilation_context.includes.to_list(),
            "Did not find {include} in includes: {includes}.".format(include = include, includes = compilation_context.includes),
        )

    for include in ctx.attr.expected_absent_includes:
        absolute_include = get_package_dir_based_path(env, include)
        asserts.true(
            env,
            absolute_include not in compilation_context.includes.to_list(),
            "Found {include} in includes: {includes}, was expected to be absent".format(include = include, includes = compilation_context.includes),
        )

    for include in ctx.attr.expected_system_includes:
        absolute_include = get_package_dir_based_path(env, include)
        asserts.true(
            env,
            absolute_include in compilation_context.system_includes.to_list(),
            "Did not find {include} in system includes: {includes}.".format(include = include, includes = compilation_context.system_includes),
        )

    for include in ctx.attr.expected_absent_system_includes:
        absolute_include = get_package_dir_based_path(env, include)
        asserts.true(
            env,
            absolute_include not in compilation_context.system_includes.to_list(),
            "Found {include} in system includes: {includes}, was expected to be absent".format(include = include, includes = compilation_context.system_includes),
        )

    return analysistest.end(env)

_cc_library_static_propagating_compilation_context_test = analysistest.make(
    _cc_library_static_propagating_compilation_context_test_impl,
    attrs = {
        "expected_hdrs": attr.label_list(),
        "expected_absent_hdrs": attr.label_list(),
        "expected_includes": attr.string_list(),
        "expected_absent_includes": attr.string_list(),
        "expected_system_includes": attr.string_list(),
        "expected_absent_system_includes": attr.string_list(),
    },
)

def _cc_library_static_propagates_deps():
    name = "_cc_library_static_propagates_deps"
    dep_name = name + "_dep"
    test_name = name + "_test"

    cc_library_static(
        name = dep_name,
        hdrs = [":hdr"],
        export_includes = ["a/b/c"],
        export_system_includes = ["d/e/f"],
        tags = ["manual"],
    )

    cc_library_static(
        name = name,
        deps = [dep_name],
        tags = ["manual"],
    )

    _cc_library_static_propagating_compilation_context_test(
        name = test_name,
        target_under_test = name,
        expected_hdrs = [":hdr"],
        expected_includes = ["a/b/c"],
        expected_system_includes = ["d/e/f"],
    )

    return test_name

def _cc_library_static_propagates_whole_archive_deps():
    name = "_cc_library_static_propagates_whole_archive_deps"
    dep_name = name + "_dep"
    test_name = name + "_test"

    cc_library_static(
        name = dep_name,
        hdrs = [":hdr"],
        export_includes = ["a/b/c"],
        export_system_includes = ["d/e/f"],
        tags = ["manual"],
    )

    cc_library_static(
        name = name,
        whole_archive_deps = [dep_name],
        tags = ["manual"],
    )

    _cc_library_static_propagating_compilation_context_test(
        name = test_name,
        target_under_test = name,
        expected_hdrs = [":hdr"],
        expected_includes = ["a/b/c"],
        expected_system_includes = ["d/e/f"],
    )

    return test_name

def _cc_library_static_propagates_dynamic_deps():
    name = "_cc_library_static_propagates_dynamic_deps"
    dep_name = name + "_dep"
    test_name = name + "_test"

    cc_library_shared(
        name = dep_name,
        hdrs = [":hdr"],
        export_includes = ["a/b/c"],
        export_system_includes = ["d/e/f"],
        tags = ["manual"],
    )

    cc_library_static(
        name = name,
        dynamic_deps = [dep_name],
        tags = ["manual"],
    )

    _cc_library_static_propagating_compilation_context_test(
        name = test_name,
        target_under_test = name,
        expected_hdrs = [":hdr"],
        expected_includes = ["a/b/c"],
        expected_system_includes = ["d/e/f"],
    )

    return test_name

def _cc_library_static_does_not_propagate_implementation_deps():
    name = "_cc_library_static_does_not_propagate_implementation_deps"
    dep_name = name + "_dep"
    test_name = name + "_test"

    cc_library_static(
        name = dep_name,
        hdrs = [":hdr"],
        export_includes = ["a/b/c"],
        export_system_includes = ["d/e/f"],
        tags = ["manual"],
    )

    cc_library_static(
        name = name,
        implementation_deps = [dep_name],
        tags = ["manual"],
    )

    _cc_library_static_propagating_compilation_context_test(
        name = test_name,
        target_under_test = name,
        expected_absent_hdrs = [":hdr"],
        expected_absent_includes = ["a/b/c"],
        expected_absent_system_includes = ["d/e/f"],
    )

    return test_name

def _cc_library_static_does_not_propagate_implementation_whole_archive_deps():
    name = "_cc_library_static_does_not_propagate_implementation_whole_archive_deps"
    dep_name = name + "_dep"
    test_name = name + "_test"

    cc_library_static(
        name = dep_name,
        hdrs = [":hdr"],
        export_includes = ["a/b/c"],
        export_system_includes = ["d/e/f"],
        tags = ["manual"],
    )

    cc_library_static(
        name = name,
        implementation_whole_archive_deps = [dep_name],
        tags = ["manual"],
    )

    _cc_library_static_propagating_compilation_context_test(
        name = test_name,
        target_under_test = name,
        expected_absent_hdrs = [":hdr"],
        expected_absent_includes = ["a/b/c"],
        expected_absent_system_includes = ["d/e/f"],
    )

    return test_name

def _cc_library_static_does_not_propagate_implementation_dynamic_deps():
    name = "_cc_library_static_does_not_propagate_implementation_dynamic_deps"
    dep_name = name + "_dep"
    test_name = name + "_test"

    cc_library_shared(
        name = dep_name,
        hdrs = [":hdr"],
        export_includes = ["a/b/c"],
        export_system_includes = ["d/e/f"],
        tags = ["manual"],
    )

    cc_library_static(
        name = name,
        implementation_dynamic_deps = [dep_name],
        tags = ["manual"],
    )

    _cc_library_static_propagating_compilation_context_test(
        name = test_name,
        target_under_test = name,
        expected_absent_hdrs = [":hdr"],
        expected_absent_includes = ["a/b/c"],
        expected_absent_system_includes = ["d/e/f"],
    )

    return test_name

def _cc_rules_do_not_allow_absolute_includes():
    name = "cc_rules_do_not_allow_absolute_includes"
    test_names = []

    DISALLOWED_INCLUDE_DIRS = [
        "art",
        "art/libnativebridge",
        "art/libnativeloader",
        "libcore",
        "libnativehelper",
        "external/apache-harmony",
        "external/apache-xml",
        "external/boringssl",
        "external/bouncycastle",
        "external/conscrypt",
        "external/icu",
        "external/okhttp",
        "external/vixl",
        "external/wycheproof",
    ]

    for include_dir in DISALLOWED_INCLUDE_DIRS:
        binary_name = name + "_binary" + "_" + include_dir
        library_headers_name = name + "_library_headers" + "_" + include_dir
        library_shared_name = name + "_library_shared" + "_" + include_dir
        library_static_name = name + "_library_static" + "_" + include_dir

        cc_binary(
            name = binary_name,
            absolute_includes = [include_dir],
            tags = ["manual"],
        )
        cc_library_headers(
            name = library_headers_name,
            export_absolute_includes = [include_dir],
            tags = ["manual"],
        )
        cc_library_shared(
            name = library_shared_name,
            absolute_includes = [include_dir],
            tags = ["manual"],
        )
        cc_library_static(
            name = library_static_name,
            absolute_includes = [include_dir],
            tags = ["manual"],
        )

        for target in [
            binary_name,
            library_headers_name,
            library_static_name,
            library_shared_name,
        ]:
            test_name = target + "_" + include_dir + "_test"
            test_names.append(test_name)
            expect_failure_test(
                name = test_name,
                target_under_test = target,
            )

    return test_names

def _cc_library_static_links_against_prebuilt_library_test_impl(ctx):
    env = analysistest.begin(ctx)
    actions = analysistest.target_actions(env)

    asserts.equals(env, 2, len(actions), "Expected actions, got %s" % actions)

    argv = actions[0].argv
    expected_output_action1 = get_output_and_package_dir_based_path(env, "libcc_library_static_links_against_prebuilt_library_objs_only.a")
    asserts.equals(env, 5, len(argv))
    asserts.equals(env, "crsPD", argv[1])
    asserts.equals(env, expected_output_action1, argv[2])
    asserts.equals(env, get_output_and_package_dir_based_path(env, paths.join("_objs", "cc_library_static_links_against_prebuilt_library_cpp", "bar.o")), argv[3])
    asserts.equals(env, "--format=gnu", argv[4])

    argv = actions[1].argv
    asserts.equals(env, 6, len(argv))
    asserts.equals(env, "cqsL", argv[1])
    asserts.equals(env, get_output_and_package_dir_based_path(env, "libcc_library_static_links_against_prebuilt_library.a"), argv[2])
    asserts.equals(env, "--format=gnu", argv[3])
    asserts.equals(env, expected_output_action1, argv[4])
    asserts.equals(env, get_package_dir_based_path(env, "foo.a"), argv[5])

    return analysistest.end(env)

_cc_library_static_links_against_prebuilt_library_test = analysistest.make(_cc_library_static_links_against_prebuilt_library_test_impl)

def _cc_library_static_links_against_prebuilt_library():
    name = "cc_library_static_links_against_prebuilt_library"
    test_name = name + "_test"
    dep_name = name + "_dep"

    cc_prebuilt_library_static(
        name = dep_name,
        static_library = "foo.a",
        tags = ["manual"],
    )

    cc_library_static(
        name = name,
        srcs = ["bar.c"],
        whole_archive_deps = [dep_name],
        tags = ["manual"],
    )

    _cc_library_static_links_against_prebuilt_library_test(
        name = test_name,
        target_under_test = name,
    )

    return test_name

def _cc_library_static_linking_object_ordering_test_impl(ctx):
    env = analysistest.begin(ctx)
    actions = analysistest.target_actions(env)

    asserts.equals(env, 1, len(actions), "Expected actions, got %s" % actions)

    outputs = actions[0].outputs.to_list()
    argv = actions[0].argv
    asserts.equals(env, 4 + len(ctx.attr.expected_objects_in_order), len(argv))
    asserts.equals(env, "crsPD", argv[1])
    asserts.equals(env, outputs[0].path, argv[2])

    for i in range(len(ctx.attr.expected_objects_in_order)):
        obj = ctx.attr.expected_objects_in_order[i]
        asserts.equals(env, obj, paths.basename(argv[3 + i]))

    asserts.equals(env, "--format=gnu", argv[-1])

    return analysistest.end(env)

_cc_library_static_linking_object_ordering_test = analysistest.make(
    _cc_library_static_linking_object_ordering_test_impl,
    attrs = {
        "expected_objects_in_order": attr.string_list(),
    },
)

def _cc_library_static_whole_archive_deps_objects_precede_target_objects():
    name = "_cc_library_static_whole_archive_deps_objects_precede_target_objects"
    dep_name = name + "_dep"
    test_name = name + "_test"

    cc_library_static(
        name = dep_name,
        srcs = ["first.c"],
        tags = ["manual"],
    )

    cc_library_static(
        name = name,
        srcs = ["second.c"],
        whole_archive_deps = [dep_name],
        tags = ["manual"],
    )

    _cc_library_static_linking_object_ordering_test(
        name = test_name,
        target_under_test = name,
        expected_objects_in_order = [
            "first.o",
            "second.o",
        ],
    )

    return test_name

def _cc_library_static_whole_archive_deps_objects_excluded_when_shared_linking():
    name = "cc_library_static_whole_archive_deps_objects_excluded_when_shared_linking"
    dep_name = name + "_dep"
    test_name = name + "_test"

    cc_library_static(
        name = dep_name,
        srcs = ["first.c"],
        tags = ["manual"],
    )

    cc_library_static(
        name = name,
        shared_linking = True,
        srcs = ["second.c"],
        whole_archive_deps = [dep_name],
        tags = ["manual"],
    )

    _cc_library_static_linking_object_ordering_test(
        name = test_name,
        target_under_test = name,
        expected_objects_in_order = [
            "second.o",
        ],
    )

    return test_name

def _cc_library_static_provides_androidmk_info():
    name = "cc_library_static_provides_androidmk_info"
    dep_name = name + "_static_dep"
    whole_archive_dep_name = name + "_whole_archive_dep"
    dynamic_dep_name = name + "_dynamic_dep"
    test_name = name + "_test"

    cc_library_static(
        name = dep_name,
        srcs = ["foo.c"],
        tags = ["manual"],
    )
    cc_library_static(
        name = whole_archive_dep_name,
        srcs = ["foo.c"],
        tags = ["manual"],
    )
    cc_library_shared(
        name = dynamic_dep_name,
        srcs = ["foo.c"],
        tags = ["manual"],
    )
    cc_library_static(
        name = name,
        srcs = ["foo.cc"],
        deps = [dep_name],
        whole_archive_deps = [whole_archive_dep_name],
        dynamic_deps = [dynamic_dep_name],
        tags = ["manual"],
    )
    android_test_name = test_name + "_android"
    android_sdk_variant_test_name = test_name + "_android_sdk_variant"
    linux_test_name = test_name + "_linux"
    target_provides_androidmk_info_test(
        name = android_test_name,
        target_under_test = name,
        expected_static_libs = [dep_name, "libc++_static", "libc++demangle"],
        expected_whole_static_libs = [whole_archive_dep_name],
        expected_shared_libs = [dynamic_dep_name, "libc_stub_libs-current", "libdl_stub_libs-current", "libm_stub_libs-current"],
        target_compatible_with = ["//build/bazel_common_rules/platforms/os:android"],
    )
    target_sdk_variant_provides_androidmk_info_test(
        name = android_sdk_variant_test_name,
        target_under_test = name,
        expected_static_libs = [dep_name],
        expected_whole_static_libs = [whole_archive_dep_name],
        expected_shared_libs = [
            dynamic_dep_name,
            # bionic NDK stubs from system_dynamic_dep_defaults
            "libc.ndk_stub_libs-current",
            "libdl.ndk_stub_libs-current",
            "libm.ndk_stub_libs-current",
            # from STL: "ndk_system".
            # sdk variants default to system STL.
            "libstdc++",
        ],
        target_compatible_with = ["//build/bazel_common_rules/platforms/os:android"],
    )
    target_provides_androidmk_info_test(
        name = linux_test_name,
        target_under_test = name,
        expected_static_libs = [dep_name, "libc++_static"],
        expected_whole_static_libs = [whole_archive_dep_name],
        expected_shared_libs = [dynamic_dep_name],
        target_compatible_with = ["//build/bazel_common_rules/platforms/os:linux"],
    )
    return [
        android_test_name,
        android_sdk_variant_test_name,
        linux_test_name,
    ]

def _cc_library_static_link_action_should_not_have_arch_cflags():
    name = "cc_library_static_link_action_should_not_have_cflags"
    cpp_compile_test_name = name + "_CppCompile_test"
    cpp_link_test_name = name + "_CppLink_test"

    # https://cs.android.com/android/platform/build/soong/+/master:cc/config/arm_device.go;l=57-59;drc=de7c7847e7e028d46fdff8268689f30163c4c231
    arm_armv7_a_cflags = ["-march=armv7-a", "-mfloat-abi=softfp"]

    cc_library_static(
        name = name,
        srcs = ["foo.cpp"],
        tags = ["manual"],
    )

    action_flags_present_only_for_mnemonic_test(
        name = cpp_compile_test_name,
        target_under_test = name + "_cpp",
        mnemonics = ["CppCompile"],
        expected_flags = arm_armv7_a_cflags,
        target_compatible_with = [
            "//build/bazel_common_rules/platforms/os:android",
            "//build/bazel/platforms/arch/variants:armv7-a-neon",
        ],
    )

    action_flags_absent_for_mnemonic_test(
        name = cpp_link_test_name,
        target_under_test = name,
        mnemonics = ["CppLink"],
        expected_absent_flags = arm_armv7_a_cflags,
        target_compatible_with = [
            "//build/bazel_common_rules/platforms/os:android",
            "//build/bazel/platforms/arch/variants:armv7-a-neon",
        ],
    )

    return [
        cpp_compile_test_name,
        cpp_link_test_name,
    ]

def _cc_library_static_defines_do_not_check_manual_binder_interfaces():
    name = "_cc_library_static_defines_do_not_check_manual_binder_interfaces"
    cpp_lib_name = name + "_cpp"
    cpp_test_name = cpp_lib_name + "_test"
    c_lib_name = name + "_c"
    c_test_name = c_lib_name + "_test"

    cc_library_static(
        name = name,
        srcs = ["a.cpp"],
        srcs_c = ["b.c"],
        tags = ["manual"],
    )
    action_flags_present_only_for_mnemonic_test(
        name = cpp_test_name,
        target_under_test = cpp_lib_name,
        mnemonics = ["CppCompile"],
        expected_flags = [
            "-DDO_NOT_CHECK_MANUAL_BINDER_INTERFACES",
        ],
    )
    action_flags_present_only_for_mnemonic_test(
        name = c_test_name,
        target_under_test = c_lib_name,
        mnemonics = ["CppCompile"],
        expected_flags = [
            "-DDO_NOT_CHECK_MANUAL_BINDER_INTERFACES",
        ],
    )

    non_allowlisted_package_cpp_name = name + "_non_allowlisted_package_cpp"
    action_flags_absent_for_mnemonic_test(
        name = non_allowlisted_package_cpp_name,
        target_under_test = "//build/bazel/examples/cc:foo_static_cpp",
        mnemonics = ["CppCompile"],
        expected_absent_flags = [
            "-DDO_NOT_CHECK_MANUAL_BINDER_INTERFACES",
        ],
    )

    return [
        cpp_test_name,
        c_test_name,
        non_allowlisted_package_cpp_name,
    ]

def cc_library_static_test_suite(name):
    native.genrule(name = "hdr", cmd = "null", outs = ["f.h"], tags = ["manual"])

    native.test_suite(
        name = name,
        tests = [
            _cc_library_static_propagates_deps(),
            _cc_library_static_propagates_whole_archive_deps(),
            _cc_library_static_propagates_dynamic_deps(),
            _cc_library_static_does_not_propagate_implementation_deps(),
            _cc_library_static_does_not_propagate_implementation_whole_archive_deps(),
            _cc_library_static_does_not_propagate_implementation_dynamic_deps(),
            _cc_library_static_links_against_prebuilt_library(),
            _cc_library_static_whole_archive_deps_objects_precede_target_objects(),
            _cc_library_static_whole_archive_deps_objects_excluded_when_shared_linking(),
        ] + (
            _cc_rules_do_not_allow_absolute_includes() +
            _cc_library_static_provides_androidmk_info() +
            _cc_library_static_link_action_should_not_have_arch_cflags() +
            _cc_library_static_defines_do_not_check_manual_binder_interfaces()
        ),
    )
