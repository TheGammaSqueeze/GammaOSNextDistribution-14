# Copyright (C) 2022 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

load("@bazel_skylib//lib:new_sets.bzl", "sets")
load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts")
load("@soong_injection//apex_toolchain:constants.bzl", "default_manifest_version")
load("//build/bazel/platforms:platform_utils.bzl", "platforms")
load("//build/bazel/rules:common.bzl", "get_dep_targets")
load("//build/bazel/rules:prebuilt_file.bzl", "prebuilt_file")
load("//build/bazel/rules:sh_binary.bzl", "sh_binary")
load("//build/bazel/rules/aidl:aidl_interface.bzl", "aidl_interface")
load("//build/bazel/rules/android:android_app_certificate.bzl", "android_app_certificate")
load("//build/bazel/rules/cc:cc_binary.bzl", "cc_binary")
load("//build/bazel/rules/cc:cc_library_headers.bzl", "cc_library_headers")
load("//build/bazel/rules/cc:cc_library_shared.bzl", "cc_library_shared")
load("//build/bazel/rules/cc:cc_library_static.bzl", "cc_library_static")
load("//build/bazel/rules/cc:cc_stub_library.bzl", "cc_stub_suite")
load("//build/bazel/rules/test_common:flags.bzl", "action_flags_present_only_for_mnemonic_test")
load("//build/bazel/rules/test_common:rules.bzl", "expect_failure_test", "target_under_test_exist_test")
load(":apex_deps_validation.bzl", "ApexDepsInfo", "apex_dep_infos_to_allowlist_strings")
load(":apex_info.bzl", "ApexInfo", "ApexMkInfo")
load(":apex_test_helpers.bzl", "test_apex")

ActionArgsInfo = provider(
    fields = {
        "argv": "The link action arguments.",
    },
)

def _canned_fs_config_test(ctx):
    env = analysistest.begin(ctx)
    actions = analysistest.target_actions(env)

    found_canned_fs_config_action = False

    def pretty_print_list(the_list):
        if not the_list:
            return "[]"
        result = "[\n"
        for item in the_list:
            result += "  \"%s\",\n" % item
        return result + "]"

    if ctx.attr.expected_extra_cat:
        append_custom_fs_config = [a for a in actions if a.mnemonic == "AppendCustomFsConfig"]
        asserts.true(env, len(append_custom_fs_config) == 1, "could not find the AppendCustomFsConfig action")
        a = append_custom_fs_config[0]
        args = a.argv[2].split(" ")  # first 2 are "/bin/bash" and "-c"
        asserts.equals(env, args[0], "cat")
        asserts.true(env, args[1].endswith("_canned_fs_config.txt"))
        asserts.true(env, args[2].endswith(ctx.attr.expected_extra_cat), "expected %s, but got %s" % (ctx.attr.expected_extra_cat, args[2]))
        asserts.equals(env, args[3], ">")
        asserts.true(env, args[4].endswith("_combined_canned_fs_config.txt"))

    for a in actions:
        if a.mnemonic != "FileWrite":
            # The canned_fs_config uses ctx.actions.write.
            continue

        outputs = a.outputs.to_list()
        if len(outputs) != 1:
            continue
        if not outputs[0].basename.endswith("_canned_fs_config.txt"):
            continue

        found_canned_fs_config_action = True

        # Don't sort -- the order is significant.
        actual_entries = a.content.split("\n")
        replacement = "64" if platforms.get_target_bitness(ctx.attr._platform_utils) == 64 else ""
        expected_entries = [x.replace("{64_OR_BLANK}", replacement) for x in ctx.attr.expected_entries]
        asserts.equals(env, pretty_print_list(expected_entries), pretty_print_list(actual_entries))

        break

    # Ensures that we actually found the canned_fs_config.txt generation action.
    asserts.true(env, found_canned_fs_config_action, "did not find the canned fs config generating action")

    return analysistest.end(env)

canned_fs_config_test = analysistest.make(
    _canned_fs_config_test,
    attrs = {
        "expected_entries": attr.string_list(
            doc = "Expected lines in the canned_fs_config.txt",
        ),
        "expected_extra_cat": attr.string(
            doc = "Filename of the custom canned fs config to be found in the AppendCustomFsConfig action",
        ),
        "_platform_utils": attr.label(
            default = Label("//build/bazel/platforms:platform_utils"),
        ),
    },
)

def _test_canned_fs_config_basic():
    name = "apex_canned_fs_config_basic"
    test_name = name + "_test"

    test_apex(name = name)

    canned_fs_config_test(
        name = test_name,
        target_under_test = name,
        expected_entries = [
            "/ 1000 1000 0755",
            "/apex_manifest.json 1000 1000 0644",
            "/apex_manifest.pb 1000 1000 0644",
            "",  # ends with a newline
        ],
    )

    return test_name

def _test_canned_fs_config_custom():
    name = "apex_canned_fs_config_custom"
    test_name = name + "_test"

    native.genrule(
        name = name + ".custom_config",
        outs = [name + ".custom.config"],
        cmd = "echo -e \"/2.bin 0 1000 0750\n/1.bin 0 1000 0777\n\" > $@",
    )

    test_apex(
        name = name,
        canned_fs_config = name + "_custom.config",
    )

    canned_fs_config_test(
        name = test_name,
        target_under_test = name,
        expected_entries = [
            "/ 1000 1000 0755",
            "/apex_manifest.json 1000 1000 0644",
            "/apex_manifest.pb 1000 1000 0644",
            "",  # ends with a newline
            # unfortunately, due to bazel analysis not being able to read the
            # contents of inputs (i.e. dynamic dependencies), we cannot test for
            # the contents of the custom config here. but, we can test that the
            # custom config is concatenated in the action command with
            # 'expected_extra_cat' below.
        ],
        expected_extra_cat = name + "_custom.config",
    )

    return test_name

def _test_canned_fs_config_binaries():
    name = "apex_canned_fs_config_binaries"
    test_name = name + "_test"

    sh_binary(
        name = "bin_sh",
        srcs = ["bin.sh"],
        tags = ["manual"],
    )

    cc_binary(
        name = "bin_cc",
        srcs = ["bin.cc"],
        tags = ["manual"],
    )

    test_apex(
        name = name,
        binaries = ["bin_sh", "bin_cc"],
    )

    canned_fs_config_test(
        name = test_name,
        target_under_test = name,
        expected_entries = [
            "/ 1000 1000 0755",
            "/apex_manifest.json 1000 1000 0644",
            "/apex_manifest.pb 1000 1000 0644",
            "/lib{64_OR_BLANK}/libc++.so 1000 1000 0644",
            "/bin/bin_cc 0 2000 0755",
            "/bin/bin_sh 0 2000 0755",
            "/bin 0 2000 0755",
            "/lib{64_OR_BLANK} 0 2000 0755",
            "",  # ends with a newline
        ],
        target_compatible_with = ["//build/bazel_common_rules/platforms/os:android"],
    )

    return test_name

def _test_canned_fs_config_native_shared_libs_arm():
    name = "apex_canned_fs_config_native_shared_libs_arm"
    test_name = name + "_test"

    cc_library_shared(
        name = name + "_lib_cc",
        srcs = [name + "_lib.cc"],
        tags = ["manual"],
    )

    cc_library_shared(
        name = name + "_lib2_cc",
        srcs = [name + "_lib2.cc"],
        tags = ["manual"],
    )

    test_apex(
        name = name,
        native_shared_libs_32 = [name + "_lib_cc"],
        native_shared_libs_64 = [name + "_lib2_cc"],
    )

    canned_fs_config_test(
        name = test_name,
        target_under_test = name,
        expected_entries = [
            "/ 1000 1000 0755",
            "/apex_manifest.json 1000 1000 0644",
            "/apex_manifest.pb 1000 1000 0644",
            "/lib/apex_canned_fs_config_native_shared_libs_arm_lib_cc.so 1000 1000 0644",
            "/lib/libc++.so 1000 1000 0644",
            "/lib 0 2000 0755",
            "",  # ends with a newline
        ],
        target_compatible_with = ["//build/bazel_common_rules/platforms/arch:arm"],
    )

    return test_name

def _test_canned_fs_config_native_shared_libs_arm64():
    name = "apex_canned_fs_config_native_shared_libs_arm64"
    test_name = name + "_test"

    cc_library_shared(
        name = name + "_lib_cc",
        srcs = [name + "_lib.cc"],
        tags = ["manual"],
    )

    cc_library_shared(
        name = name + "_lib2_cc",
        srcs = [name + "_lib2.cc"],
        tags = ["manual"],
    )

    test_apex(
        name = name,
        native_shared_libs_32 = [name + "_lib_cc"],
        native_shared_libs_64 = [name + "_lib2_cc"],
    )

    canned_fs_config_test(
        name = test_name,
        target_under_test = name,
        expected_entries = [
            "/ 1000 1000 0755",
            "/apex_manifest.json 1000 1000 0644",
            "/apex_manifest.pb 1000 1000 0644",
            "/lib/apex_canned_fs_config_native_shared_libs_arm64_lib_cc.so 1000 1000 0644",
            "/lib/libc++.so 1000 1000 0644",
            "/lib64/apex_canned_fs_config_native_shared_libs_arm64_lib2_cc.so 1000 1000 0644",
            "/lib64/libc++.so 1000 1000 0644",
            "/lib 0 2000 0755",
            "/lib64 0 2000 0755",
            "",  # ends with a newline
        ],
        target_compatible_with = ["//build/bazel_common_rules/platforms/arch:arm64"],
    )

    return test_name

def _test_canned_fs_config_prebuilts():
    name = "apex_canned_fs_config_prebuilts"
    test_name = name + "_test"

    prebuilt_file(
        name = "file",
        src = "file.txt",
        dir = "etc",
        tags = ["manual"],
    )

    prebuilt_file(
        name = "nested_file_in_dir",
        src = "file2.txt",
        dir = "etc/nested",
        tags = ["manual"],
    )

    prebuilt_file(
        name = "renamed_file_in_dir",
        src = "file3.txt",
        dir = "etc",
        filename = "renamed_file3.txt",
        tags = ["manual"],
    )

    test_apex(
        name = name,
        prebuilts = [
            ":file",
            ":nested_file_in_dir",
            ":renamed_file_in_dir",
        ],
    )

    canned_fs_config_test(
        name = test_name,
        target_under_test = name,
        expected_entries = [
            "/ 1000 1000 0755",
            "/apex_manifest.json 1000 1000 0644",
            "/apex_manifest.pb 1000 1000 0644",
            "/etc/file 1000 1000 0644",
            "/etc/nested/nested_file_in_dir 1000 1000 0644",
            "/etc/renamed_file3.txt 1000 1000 0644",
            "/etc 0 2000 0755",
            "/etc/nested 0 2000 0755",
            "",  # ends with a newline
        ],
    )

    return test_name

def _test_canned_fs_config_prebuilts_sort_order():
    name = "apex_canned_fs_config_prebuilts_sort_order"
    test_name = name + "_test"

    prebuilt_file(
        name = "file_a",
        src = "file_a.txt",
        dir = "etc/a",
        tags = ["manual"],
    )

    prebuilt_file(
        name = "file_b",
        src = "file_b.txt",
        dir = "etc/b",
        tags = ["manual"],
    )

    prebuilt_file(
        name = "file_a_c",
        src = "file_a_c.txt",
        dir = "etc/a/c",
        tags = ["manual"],
    )

    test_apex(
        name = name,
        prebuilts = [
            ":file_a",
            ":file_b",
            ":file_a_c",
        ],
    )

    canned_fs_config_test(
        name = test_name,
        target_under_test = name,
        expected_entries = [
            "/ 1000 1000 0755",
            "/apex_manifest.json 1000 1000 0644",
            "/apex_manifest.pb 1000 1000 0644",
            "/etc/a/c/file_a_c 1000 1000 0644",
            "/etc/a/file_a 1000 1000 0644",
            "/etc/b/file_b 1000 1000 0644",
            "/etc 0 2000 0755",
            "/etc/a 0 2000 0755",
            "/etc/a/c 0 2000 0755",
            "/etc/b 0 2000 0755",
            "",  # ends with a newline
        ],
    )

    return test_name

def _test_canned_fs_config_runtime_deps():
    name = "apex_canned_fs_config_runtime_deps"
    test_name = name + "_test"

    cc_library_shared(
        name = name + "_runtime_dep_3",
        srcs = ["lib2.cc"],
        tags = ["manual"],
    )

    cc_library_static(
        name = name + "_static_lib",
        srcs = ["lib3.cc"],
        runtime_deps = [name + "_runtime_dep_3"],
        tags = ["manual"],
    )

    cc_library_shared(
        name = name + "_runtime_dep_2",
        srcs = ["lib2.cc"],
        tags = ["manual"],
    )

    cc_library_shared(
        name = name + "_runtime_dep_1",
        srcs = ["lib.cc"],
        runtime_deps = [name + "_runtime_dep_2"],
        tags = ["manual"],
    )

    cc_binary(
        name = name + "_bin_cc",
        srcs = ["bin.cc"],
        runtime_deps = [name + "_runtime_dep_1"],
        deps = [name + "_static_lib"],
        tags = ["manual"],
    )

    test_apex(
        name = name,
        binaries = [name + "_bin_cc"],
    )

    canned_fs_config_test(
        name = test_name,
        target_under_test = name,
        expected_entries = [
            "/ 1000 1000 0755",
            "/apex_manifest.json 1000 1000 0644",
            "/apex_manifest.pb 1000 1000 0644",
            "/lib{64_OR_BLANK}/%s_runtime_dep_1.so 1000 1000 0644" % name,
            "/lib{64_OR_BLANK}/%s_runtime_dep_2.so 1000 1000 0644" % name,
            "/lib{64_OR_BLANK}/libc++.so 1000 1000 0644",
            "/bin/%s_bin_cc 0 2000 0755" % name,
            "/bin 0 2000 0755",
            "/lib{64_OR_BLANK} 0 2000 0755",
            "",  # ends with a newline
        ],
        target_compatible_with = ["//build/bazel_common_rules/platforms/os:android"],
    )

    return test_name

def _apex_manifest_test(ctx):
    env = analysistest.begin(ctx)
    actions = analysistest.target_actions(env)

    conv_apex_manifest_action = [a for a in actions if a.mnemonic == "ConvApexManifest"][0]

    apexer_action = [a for a in actions if a.mnemonic == "Apexer"][0]
    argv = apexer_action.argv[:-1] + apexer_action.argv[-1].split(" ")
    manifest_index = argv.index("--manifest")
    manifest_path = argv[manifest_index + 1]

    asserts.equals(
        env,
        conv_apex_manifest_action.outputs.to_list()[0].path,
        manifest_path,
        "the generated apex manifest protobuf is used as input to apexer",
    )
    asserts.true(
        env,
        manifest_path.endswith(".pb"),
        "the generated apex manifest should be a .pb file",
    )

    if ctx.attr.expected_min_sdk_version != "":
        flag_index = argv.index("--min_sdk_version")
        min_sdk_version_argv = argv[flag_index + 1]
        asserts.equals(
            env,
            ctx.attr.expected_min_sdk_version,
            min_sdk_version_argv,
        )

    return analysistest.end(env)

apex_manifest_test_attr = dict(
    impl = _apex_manifest_test,
    attrs = {
        "expected_min_sdk_version": attr.string(),
    },
)

apex_manifest_test = analysistest.make(
    **apex_manifest_test_attr
)

apex_manifest_global_min_sdk_current_test = analysistest.make(
    config_settings = {
        "@//build/bazel/rules/apex:unbundled_build_target_sdk_with_api_fingerprint": False,
    },
    **apex_manifest_test_attr
)

apex_manifest_global_min_sdk_override_tiramisu_test = analysistest.make(
    config_settings = {
        "//command_line_option:platforms": "@//build/bazel/tests/products:aosp_arm64_for_testing_min_sdk_version_override_tiramisu",
        "@//build/bazel/rules/apex:unbundled_build_target_sdk_with_api_fingerprint": False,
    },
    **apex_manifest_test_attr
)

def _test_apex_manifest():
    name = "apex_manifest"
    test_name = name + "_test"

    test_apex(name = name)

    apex_manifest_test(
        name = test_name,
        target_under_test = name,
    )

    return test_name

def _test_apex_manifest_min_sdk_version():
    name = "apex_manifest_min_sdk_version"
    test_name = name + "_test"

    test_apex(
        name = name,
        min_sdk_version = "30",
    )

    apex_manifest_test(
        name = test_name,
        target_under_test = name,
        expected_min_sdk_version = "30",
    )

    return test_name

def _test_apex_manifest_min_sdk_version_current():
    name = "apex_manifest_min_sdk_version_current"
    test_name = name + "_test"

    test_apex(
        name = name,
        min_sdk_version = "current",
    )

    # this test verifies min_sdk_version without use_api_fingerprint
    apex_manifest_global_min_sdk_current_test(
        name = test_name,
        target_under_test = name,
        expected_min_sdk_version = "10000",
    )

    return test_name

def _test_apex_manifest_min_sdk_version_override():
    name = "apex_manifest_min_sdk_version_override"
    test_name = name + "_test"

    test_apex(
        name = name,
        min_sdk_version = "30",
    )

    # this test verifies min_sdk_version without use_api_fingerprint
    apex_manifest_global_min_sdk_override_tiramisu_test(
        name = test_name,
        target_under_test = name,
        expected_min_sdk_version = "33",  # overriden to 33
    )

    return test_name

def _apex_native_libs_requires_provides_test(ctx):
    env = analysistest.begin(ctx)
    target_under_test = analysistest.target_under_test(env)
    asserts.equals(
        env,
        [t.label for t in ctx.attr.requires_native_libs],  # expected
        target_under_test[ApexInfo].requires_native_libs,  # actual
        "did not get expected requires_native_libs",
    )
    asserts.equals(
        env,
        [t.label for t in ctx.attr.provides_native_libs],
        target_under_test[ApexInfo].provides_native_libs,
        "did not get expected provides_native_libs",
    )
    asserts.equals(
        env,
        ctx.attr.make_modules_to_install,
        target_under_test[ApexMkInfo].make_modules_to_install,
        "did not get expected make_modules_to_install",
    )

    # Compare the argv of the jsonmodify action that updates the apex
    # manifest with information about provided and required libs.
    actions = analysistest.target_actions(env)
    action = [a for a in actions if a.mnemonic == "ApexManifestModify"][0]
    requires_argv_index = action.argv.index("requireNativeLibs") + 1
    provides_argv_index = action.argv.index("provideNativeLibs") + 1

    for idx, requires in enumerate(ctx.attr.requires_native_libs):
        asserts.equals(
            env,
            requires.label.name + ".so",  # expected
            action.argv[requires_argv_index + idx],  # actual
        )

    for idx, provides in enumerate(ctx.attr.provides_native_libs):
        asserts.equals(
            env,
            provides.label.name + ".so",
            action.argv[provides_argv_index + idx],
        )

    return analysistest.end(env)

apex_native_libs_requires_provides_test = analysistest.make(
    _apex_native_libs_requires_provides_test,
    attrs = {
        "make_modules_to_install": attr.string_list(doc = "make module names that should be installed to system"),
        "provides_argv": attr.string_list(),
        "provides_native_libs": attr.label_list(doc = "bazel target names of libs provided for dynamic linking"),
        "requires_argv": attr.string_list(),
        "requires_native_libs": attr.label_list(doc = "bazel target names of libs required for dynamic linking"),
    },
)

def _test_apex_manifest_dependencies_nodep():
    name = "apex_manifest_dependencies_nodep"
    test_name = name + "_test"

    cc_library_shared(
        name = name + "_lib_nodep",
        stl = "none",
        system_dynamic_deps = [],
        tags = ["manual"],
    )

    test_apex(
        name = name,
        native_shared_libs_32 = [name + "_lib_nodep"],
        native_shared_libs_64 = [name + "_lib_nodep"],
    )

    apex_native_libs_requires_provides_test(
        name = test_name,
        target_under_test = name,
        requires_native_libs = [],
        provides_native_libs = [],
        make_modules_to_install = [],
    )

    return test_name

def _test_apex_manifest_dependencies_cc_library_shared_bionic_deps():
    name = "apex_manifest_dependencies_cc_library_shared_bionic_deps"
    test_name = name + "_test"

    cc_library_shared(
        name = name + "_lib",
        # implicit bionic system_dynamic_deps
        tags = ["manual"],
    )

    test_apex(
        name = name,
        native_shared_libs_32 = [name + "_lib"],
        native_shared_libs_64 = [name + "_lib"],
    )

    apex_native_libs_requires_provides_test(
        name = test_name,
        target_under_test = name,
        requires_native_libs = [
            "//bionic/libc",
            "//bionic/libdl",
            "//bionic/libm",
        ],
        provides_native_libs = [],
        make_modules_to_install = [],
    )

    return test_name

def _test_apex_manifest_dependencies_cc_binary_bionic_deps():
    name = "apex_manifest_dependencies_cc_binary_bionic_deps"
    test_name = name + "_test"

    cc_binary(
        name = name + "_bin",
        # implicit bionic system_deps
        tags = ["manual"],
    )

    test_apex(
        name = name,
        binaries = [name + "_bin"],
    )

    apex_native_libs_requires_provides_test(
        name = test_name,
        target_under_test = name,
        requires_native_libs = [
            "//bionic/libc",
            "//bionic/libdl",
            "//bionic/libm",
        ],
        provides_native_libs = [],
        make_modules_to_install = [],
    )

    return test_name

def _test_apex_manifest_dependencies_requires():
    name = "apex_manifest_dependencies_requires"
    test_name = name + "_test"

    cc_library_shared(
        name = name + "_lib_with_dep",
        system_dynamic_deps = [],
        stl = "none",
        implementation_dynamic_deps = select({
            "//build/bazel/rules/apex:android-in_apex": [name + "_libfoo_stub_libs_current"],
            "//build/bazel/rules/apex:android-non_apex": [name + "_libfoo"],
        }),
        tags = ["manual"],
        stubs_symbol_file = name + "_lib_with_dep" + ".map.txt",
    )

    native.genrule(
        name = name + "_genrule_lib_with_dep_map_txt",
        outs = [name + "_lib_with_dep.map.txt"],
        cmd = "touch $@",
        tags = ["manual"],
    )

    cc_stub_suite(
        name = name + "_lib_with_dep_stub_libs",
        soname = name + "_lib_with_dep.so",
        source_library_label = ":" + name + "_lib_with_dep",
        symbol_file = name + "_lib_with_dep.map.txt",
        versions = ["30"],
    )

    cc_library_shared(
        name = name + "_libfoo",
        system_dynamic_deps = [],
        stl = "none",
        tags = ["manual"],
        stubs_symbol_file = name + "_libfoo" + ".map.txt",
    )

    native.genrule(
        name = name + "_genrule_libfoo_map_txt",
        outs = [name + "_libfoo.map.txt"],
        cmd = "touch $@",
        tags = ["manual"],
    )

    cc_stub_suite(
        name = name + "_libfoo_stub_libs",
        soname = name + "_libfoo.so",
        source_library_label = ":" + name + "_libfoo",
        symbol_file = name + "_libfoo.map.txt",
        versions = ["30"],
    )

    test_apex(
        name = name,
        native_shared_libs_32 = [name + "_lib_with_dep"],
        native_shared_libs_64 = [name + "_lib_with_dep"],
    )

    apex_native_libs_requires_provides_test(
        name = test_name,
        target_under_test = name,
        requires_native_libs = [name + "_libfoo"],
        provides_native_libs = [name + "_lib_with_dep"],
        make_modules_to_install = [name + "_libfoo"],
        target_compatible_with = ["//build/bazel_common_rules/platforms/os:android"],
    )

    return test_name

def _test_apex_manifest_dependencies_provides():
    name = "apex_manifest_dependencies_provides"
    test_name = name + "_test"

    cc_library_shared(
        name = name + "_libfoo",
        system_dynamic_deps = [],
        stl = "none",
        tags = ["manual"],
        stubs_symbol_file = name + "_libfoo" + ".map.txt",
    )

    native.genrule(
        name = name + "_genrule_libfoo_map_txt",
        outs = [name + "_libfoo.map.txt"],
        cmd = "touch $@",
        tags = ["manual"],
    )

    cc_stub_suite(
        name = name + "_libfoo_stub_libs",
        soname = name + "_libfoo.so",
        source_library_label = ":" + name + "_libfoo",
        symbol_file = name + "_libfoo.map.txt",
        versions = ["30"],
    )

    test_apex(
        name = name,
        native_shared_libs_32 = [name + "_libfoo"],
        native_shared_libs_64 = [name + "_libfoo"],
    )

    apex_native_libs_requires_provides_test(
        name = test_name,
        target_under_test = name,
        requires_native_libs = [],
        provides_native_libs = [name + "_libfoo"],
        make_modules_to_install = [],
    )

    return test_name

def _test_apex_manifest_dependencies_selfcontained():
    name = "apex_manifest_dependencies_selfcontained"
    test_name = name + "_test"

    cc_library_shared(
        name = name + "_lib_with_dep",
        system_dynamic_deps = [],
        stl = "none",
        implementation_dynamic_deps = select({
            "//build/bazel/rules/apex:android-in_apex": [name + "_libfoo_stub_libs_current"],
            "//build/bazel/rules/apex:android-non_apex": [name + "_libfoo"],
        }),
        tags = ["manual"],
        stubs_symbol_file = name + "_lib_with_dep" + ".map.txt",
    )

    native.genrule(
        name = name + "_genrule_lib-with_dep_map_txt",
        outs = [name + "_lib_with_dep.map.txt"],
        cmd = "touch $@",
        tags = ["manual"],
    )

    cc_stub_suite(
        name = name + "_lib_with_dep_stub_libs",
        soname = name + "_lib_with_dep.so",
        source_library_label = ":" + name + "_lib_with_dep",
        symbol_file = name + "_lib_with_dep.map.txt",
        versions = ["30"],
    )

    cc_library_shared(
        name = name + "_libfoo",
        system_dynamic_deps = [],
        stl = "none",
        tags = ["manual"],
        stubs_symbol_file = name + "_libfoo" + ".map.txt",
    )

    native.genrule(
        name = name + "_genrule_libfoo_map_txt",
        outs = [name + "_libfoo.map.txt"],
        cmd = "touch $@",
        tags = ["manual"],
    )

    cc_stub_suite(
        name = name + "_libfoo_stub_libs",
        soname = name + "_libfoo.so",
        source_library_label = ":" + name + "_libfoo",
        symbol_file = name + "_libfoo.map.txt",
        versions = ["30"],
    )

    test_apex(
        name = name,
        native_shared_libs_32 = [
            name + "_lib_with_dep",
            name + "_libfoo",
        ],
        native_shared_libs_64 = [
            name + "_lib_with_dep",
            name + "_libfoo",
        ],
    )

    apex_native_libs_requires_provides_test(
        name = test_name,
        target_under_test = name,
        requires_native_libs = [],
        provides_native_libs = [
            name + "_lib_with_dep",
            name + "_libfoo",
        ],
        make_modules_to_install = [],
        target_compatible_with = ["//build/bazel_common_rules/platforms/os:android"],
    )

    return test_name

def _test_apex_manifest_dependencies_cc_binary():
    name = "apex_manifest_dependencies_cc_binary"
    test_name = name + "_test"

    cc_binary(
        name = name + "_bin",
        stl = "none",
        system_deps = [],
        dynamic_deps = [
            name + "_lib_with_dep",
        ] + select({
            "//build/bazel/rules/apex:android-in_apex": [name + "_librequires2_stub_libs_current"],
            "//build/bazel/rules/apex:android-non_apex": [name + "_librequires2"],
        }),
        tags = ["manual"],
    )

    cc_library_shared(
        name = name + "_lib_with_dep",
        system_dynamic_deps = [],
        stl = "none",
        implementation_dynamic_deps = select({
            "//build/bazel/rules/apex:android-in_apex": [name + "_librequires_stub_libs_current"],
            "//build/bazel/rules/apex:android-non_apex": [name + "_librequires"],
        }),
        tags = ["manual"],
    )

    cc_library_shared(
        name = name + "_librequires",
        system_dynamic_deps = [],
        stl = "none",
        tags = ["manual"],
        stubs_symbol_file = name + "_librequires" + ".map.txt",
    )

    native.genrule(
        name = name + "_genrule_librequires_map_txt",
        outs = [name + "_librequires.map.txt"],
        cmd = "touch $@",
        tags = ["manual"],
    )

    cc_stub_suite(
        name = name + "_librequires_stub_libs",
        soname = name + "_librequires.so",
        source_library_label = ":" + name + "_librequires",
        symbol_file = name + "_librequires.map.txt",
        versions = ["30"],
    )

    cc_library_shared(
        name = name + "_librequires2",
        system_dynamic_deps = [],
        stl = "none",
        tags = ["manual"],
        stubs_symbol_file = name + "_librequires2.map.txt",
    )

    native.genrule(
        name = name + "_genrule_librequires2_map_txt",
        outs = [name + "_librequires2.map.txt"],
        cmd = "touch $@",
        tags = ["manual"],
    )

    cc_stub_suite(
        name = name + "_librequires2_stub_libs",
        soname = name + "_librequires2.so",
        source_library_label = ":" + name + "_librequires2",
        symbol_file = name + "_librequires2.map.txt",
        versions = ["30"],
    )

    test_apex(
        name = name,
        binaries = [name + "_bin"],
    )

    apex_native_libs_requires_provides_test(
        name = test_name,
        target_under_test = name,
        requires_native_libs = [
            name + "_librequires",
            name + "_librequires2",
        ],
        make_modules_to_install = [
            name + "_librequires",
            name + "_librequires2",
        ],
        target_compatible_with = ["//build/bazel_common_rules/platforms/os:android"],
    )

    return test_name

def _action_args_test(ctx):
    env = analysistest.begin(ctx)
    actions = analysistest.target_actions(env)

    action = [a for a in actions if a.mnemonic == ctx.attr.action_mnemonic][0]
    argv = action.argv[:-1] + action.argv[-1].split(" ")
    flag_idx = argv.index(ctx.attr.expected_args[0])

    for i, expected_arg in enumerate(ctx.attr.expected_args):
        asserts.equals(
            env,
            expected_arg,
            argv[flag_idx + i],
        )

    return analysistest.end(env)

_action_args_test_attrs = {
    "action_mnemonic": attr.string(mandatory = True),
    "expected_args": attr.string_list(mandatory = True),
}

action_args_test = analysistest.make(
    _action_args_test,
    attrs = _action_args_test_attrs,
)

def _test_logging_parent_flag():
    name = "logging_parent"
    test_name = name + "_test"

    test_apex(
        name = name,
        logging_parent = "logging.parent",
    )

    action_args_test(
        name = test_name,
        target_under_test = name,
        action_mnemonic = "Apexer",
        expected_args = [
            "--logging_parent",
            "logging.parent",
        ],
    )

    return test_name

def _test_default_apex_manifest_version():
    name = "default_apex_manifest_version"
    test_name = name + "_test"

    test_apex(
        name = name,
    )

    action_args_test(
        name = test_name,
        target_under_test = name,
        action_mnemonic = "ApexManifestModify",
        expected_args = [
            "-se",
            "version",
            "0",
            str(default_manifest_version),
        ],
    )

    return test_name

action_args_with_overrides_test = analysistest.make(
    _action_args_test,
    attrs = _action_args_test_attrs,
    config_settings = {
        "//command_line_option:platforms": "@//build/bazel/tests/products:aosp_arm64_for_testing_with_overrides_and_app_cert",
    },
)

def _test_package_name():
    name = "package_name"
    test_name = name + "_test"

    test_apex(
        name = name,
        package_name = "my.package.name",
    )

    action_args_test(
        name = test_name,
        target_under_test = name,
        action_mnemonic = "Apexer",
        expected_args = [
            "--override_apk_package_name",
            "my.package.name",
        ],
    )

    return test_name

def _test_package_name_override_from_config():
    name = "package_name_override_from_config"
    test_name = name + "_test"

    test_apex(name = name)

    action_args_with_overrides_test(
        name = test_name,
        target_under_test = name,
        action_mnemonic = "Apexer",
        expected_args = [
            "--override_apk_package_name",
            "another.package",
        ],
    )

    return test_name

action_args_with_override_apex_manifest_default_version_test = analysistest.make(
    _action_args_test,
    attrs = _action_args_test_attrs,
    # Wouldn't it be nice if it's possible to set the config_setting from the test callsite..
    config_settings = {
        "@//build/bazel/rules/apex:override_apex_manifest_default_version": "1234567890",
    },
)

def _test_override_apex_manifest_version():
    name = "override_apex_manifest_version"
    test_name = name + "_test"

    test_apex(
        name = name,
    )

    action_args_with_override_apex_manifest_default_version_test(
        name = test_name,
        target_under_test = name,
        action_mnemonic = "ApexManifestModify",
        expected_args = [
            "-se",
            "version",
            "0",
            "1234567890",
        ],
    )

    return test_name

def _file_contexts_args_test(ctx):
    env = analysistest.begin(ctx)
    actions = analysistest.target_actions(env)

    file_contexts_action = [a for a in actions if a.mnemonic == "GenerateApexFileContexts"][0]

    # GenerateApexFileContexts is a run_shell action.
    # ["/bin/bash", "c", "<args>"]
    cmd = file_contexts_action.argv[2]

    for expected_arg in ctx.attr.expected_args:
        asserts.true(
            env,
            expected_arg in cmd,
            "failed to find '%s' in '%s'" % (expected_arg, cmd),
        )

    return analysistest.end(env)

file_contexts_args_test = analysistest.make(
    _file_contexts_args_test,
    attrs = {
        "expected_args": attr.string_list(mandatory = True),
    },
)

def _test_generate_file_contexts():
    name = "apex_manifest_pb_file_contexts"
    test_name = name + "_test"

    test_apex(
        name = name,
    )

    file_contexts_args_test(
        name = test_name,
        target_under_test = name,
        expected_args = [
            "/apex_manifest\\\\.pb u:object_r:system_file:s0",
            "/ u:object_r:system_file:s0",
        ],
    )

    return test_name

def _min_sdk_version_failure_test_impl(ctx):
    env = analysistest.begin(ctx)

    asserts.expect_failure(
        env,
        "min_sdk_version %s cannot be lower than the dep's min_sdk_version %s" %
        (ctx.attr.apex_min, ctx.attr.dep_min),
    )

    return analysistest.end(env)

min_sdk_version_failure_test = analysistest.make(
    _min_sdk_version_failure_test_impl,
    expect_failure = True,
    attrs = {
        "apex_min": attr.string(),
        "dep_min": attr.string(),
    },
)

def _test_min_sdk_version_failure():
    name = "min_sdk_version_failure"
    test_name = name + "_test"

    cc_library_shared(
        name = name + "_lib_cc",
        srcs = [name + "_lib.cc"],
        tags = ["manual"],
        min_sdk_version = "32",
    )

    test_apex(
        name = name,
        native_shared_libs_32 = [name + "_lib_cc"],
        min_sdk_version = "30",
    )

    min_sdk_version_failure_test(
        name = test_name,
        target_under_test = name,
        apex_min = "30",
        dep_min = "32",
    )

    return test_name

def _test_min_sdk_version_failure_transitive():
    name = "min_sdk_version_failure_transitive"
    test_name = name + "_test"

    cc_library_shared(
        name = name + "_lib_cc",
        dynamic_deps = [name + "_lib2_cc"],
        tags = ["manual"],
    )

    cc_library_shared(
        name = name + "_lib2_cc",
        srcs = [name + "_lib2.cc"],
        tags = ["manual"],
        min_sdk_version = "32",
    )

    test_apex(
        name = name,
        native_shared_libs_32 = [name + "_lib_cc"],
        min_sdk_version = "30",
    )

    min_sdk_version_failure_test(
        name = test_name,
        target_under_test = name,
        apex_min = "30",
        dep_min = "32",
    )

    return test_name

def _apex_certificate_test(ctx):
    env = analysistest.begin(ctx)
    target_under_test = analysistest.target_under_test(env)
    container_key_info = target_under_test[ApexInfo].container_key_info

    asserts.equals(env, ctx.attr.expected_pem_path, container_key_info.pem.path)
    asserts.equals(env, ctx.attr.expected_pk8_path, container_key_info.pk8.path)

    return analysistest.end(env)

apex_certificate_test = analysistest.make(
    _apex_certificate_test,
    attrs = {
        "expected_pem_path": attr.string(),
        "expected_pk8_path": attr.string(),
    },
)

apex_certificate_with_overrides_test = analysistest.make(
    _apex_certificate_test,
    attrs = {
        "expected_pem_path": attr.string(),
        "expected_pk8_path": attr.string(),
    },
    config_settings = {
        "//command_line_option:platforms": "@//build/bazel/tests/products:aosp_arm64_for_testing_with_overrides_and_app_cert",
    },
)

def _test_apex_certificate_none():
    name = "apex_certificate_none"
    test_name = name + "_test"

    test_apex(
        name = name,
        certificate = None,
    )

    apex_certificate_test(
        name = test_name,
        target_under_test = name,
        expected_pem_path = "build/make/target/product/security/testkey.x509.pem",
        expected_pk8_path = "build/make/target/product/security/testkey.pk8",
    )

    return test_name

def _test_apex_certificate_name():
    name = "apex_certificate_name"
    test_name = name + "_test"

    test_apex(
        name = name,
        certificate = None,
        certificate_name = "shared",  # use something other than testkey
    )

    apex_certificate_test(
        name = test_name,
        target_under_test = name,
        expected_pem_path = "build/make/target/product/security/shared.x509.pem",
        expected_pk8_path = "build/make/target/product/security/shared.pk8",
    )

    return test_name

def _test_apex_certificate_label():
    name = "apex_certificate_label"
    test_name = name + "_test"

    android_app_certificate(
        name = name + "_cert",
        certificate = name,
        tags = ["manual"],
    )

    test_apex(
        name = name,
        certificate = name + "_cert",
    )

    apex_certificate_test(
        name = test_name,
        target_under_test = name,
        expected_pem_path = "build/bazel/rules/apex/apex_certificate_label.x509.pem",
        expected_pk8_path = "build/bazel/rules/apex/apex_certificate_label.pk8",
    )

    return test_name

def _test_apex_certificate_label_with_overrides():
    name = "apex_certificate_label_with_overrides"
    test_name = name + "_test"

    android_app_certificate(
        name = name + "_cert",
        certificate = name,
        tags = ["manual"],
    )

    android_app_certificate(
        name = name + "_another_cert",
        certificate = name + "_another_cert",
        tags = ["manual"],
    )

    test_apex(
        name = name,
        certificate = name + "_cert",
    )

    apex_certificate_with_overrides_test(
        name = test_name,
        target_under_test = name,
        expected_pem_path = "build/bazel/rules/apex/apex_certificate_label_with_overrides_another_cert.x509.pem",
        expected_pk8_path = "build/bazel/rules/apex/apex_certificate_label_with_overrides_another_cert.pk8",
    )

    return test_name

def _min_sdk_version_apex_inherit_test_impl(ctx):
    env = analysistest.begin(ctx)
    target_under_test = analysistest.target_under_test(env)
    argv = target_under_test[ActionArgsInfo].argv

    found = False
    for arg in argv:
        if arg.startswith("--target="):
            found = True
            asserts.true(
                env,
                arg.endswith(ctx.attr.apex_min),
                "Incorrect --target flag: %s %s" % (arg, ctx.attr.apex_min),
            )

    asserts.true(
        env,
        found,
        "No --target flag found: %s" % argv,
    )

    return analysistest.end(env)

def _feature_check_aspect_impl(target, ctx):
    rules_propagate_src = [
        "_bssl_hash_injection",
        "stripped_shared_library",
        "versioned_shared_library",
    ]

    argv = []
    if ctx.rule.kind == "cc_shared_library" and target.label.name == ctx.attr.cc_target:
        link_actions = [a for a in target.actions if a.mnemonic == "CppLink"]
        argv = link_actions[0].argv
    elif ctx.rule.kind in rules_propagate_src and hasattr(ctx.rule.attr, "src"):
        argv = ctx.rule.attr.src[ActionArgsInfo].argv
    elif ctx.rule.kind == "_cc_library_shared_proxy" and hasattr(ctx.rule.attr, "shared"):
        argv = ctx.rule.attr.shared[0][ActionArgsInfo].argv
    elif ctx.rule.kind == "_apex" and hasattr(ctx.rule.attr, "native_shared_libs_32"):
        argv = ctx.rule.attr.native_shared_libs_32[0][ActionArgsInfo].argv

    return [
        ActionArgsInfo(
            argv = argv,
        ),
    ]

feature_check_aspect = aspect(
    implementation = _feature_check_aspect_impl,
    attrs = {
        "cc_target": attr.string(values = [
            # This has to mirror the test impl library names
            "min_sdk_version_apex_inherit_lib_cc_unstripped",
            "min_sdk_version_apex_inherit_override_min_sdk_tiramisu_lib_cc_unstripped",
        ]),
    },
    attr_aspects = ["native_shared_libs_32", "shared", "src"],
)

min_sdk_version_apex_inherit_test_attrs = dict(
    impl = _min_sdk_version_apex_inherit_test_impl,
    attrs = {
        "apex_min": attr.string(),
        "cc_target": attr.string(),
    },
    # We need to use aspect to examine the dependencies' actions of the apex
    # target as the result of the transition, checking the dependencies directly
    # using names will give you the info before the transition takes effect.
    extra_target_under_test_aspects = [feature_check_aspect],
)

min_sdk_version_apex_inherit_test = analysistest.make(
    **min_sdk_version_apex_inherit_test_attrs
)

min_sdk_version_apex_inherit_override_min_sdk_tiramisu_test = analysistest.make(
    config_settings = {
        "//command_line_option:platforms": "@//build/bazel/tests/products:aosp_arm64_for_testing_min_sdk_version_override_tiramisu",
    },
    **min_sdk_version_apex_inherit_test_attrs
)

def _test_min_sdk_version_apex_inherit():
    name = "min_sdk_version_apex_inherit"
    test_name = name + "_test"
    cc_name = name + "_lib_cc"
    apex_min = "29"

    cc_library_shared(
        name = cc_name,
        srcs = [name + "_lib.cc"],
        tags = ["manual"],
        min_sdk_version = "apex_inherit",
    )

    test_apex(
        name = name,
        native_shared_libs_32 = [cc_name],
        min_sdk_version = apex_min,
    )

    min_sdk_version_apex_inherit_test(
        name = test_name,
        target_under_test = name,
        apex_min = apex_min,
        cc_target = cc_name + "_unstripped",
    )

    return test_name

def _test_min_sdk_version_apex_inherit_override_min_sdk_tiramisu():
    name = "min_sdk_version_apex_inherit_override_min_sdk_tiramisu"
    test_name = name + "_test"
    cc_name = name + "_lib_cc"

    cc_library_shared(
        name = cc_name,
        srcs = [name + "_lib.cc"],
        tags = ["manual"],
        min_sdk_version = "apex_inherit",
    )

    test_apex(
        name = name,
        native_shared_libs_32 = [cc_name],
        min_sdk_version = "29",
    )

    min_sdk_version_apex_inherit_override_min_sdk_tiramisu_test(
        name = test_name,
        target_under_test = name,
        apex_min = "33",  # the apex transition forced the apex min_sdk_version to be 33
        cc_target = cc_name + "_unstripped",
    )

    return test_name

def _apex_provides_base_zip_files_test_impl(ctx):
    env = analysistest.begin(ctx)
    target_under_test = analysistest.target_under_test(env)

    # The particular name of the file isn't important as it just gets zipped with the other apex files for other architectures
    asserts.true(
        env,
        target_under_test[ApexInfo].base_file != None,
        "Expected base_file to exist, but found None %s" % target_under_test[ApexInfo].base_file,
    )

    asserts.equals(
        env,
        target_under_test[ApexInfo].base_with_config_zip.basename,
        # name is important here because the file gets disted and then referenced by name
        ctx.attr.apex_name + ".apex-base.zip",
        "Expected base file with config zip to have name ending with , but found %s" % target_under_test[ApexInfo].base_with_config_zip.basename,
    )

    return analysistest.end(env)

apex_provides_base_zip_files_test = analysistest.make(
    _apex_provides_base_zip_files_test_impl,
    attrs = {
        "apex_name": attr.string(),
    },
)

def _test_apex_provides_base_zip_files():
    name = "apex_provides_base_zip_files"
    test_name = name + "_test"

    test_apex(name = name)

    apex_provides_base_zip_files_test(
        name = test_name,
        target_under_test = name,
        apex_name = name,
    )

    return test_name

def _apex_testonly_with_manifest_test_impl(ctx):
    env = analysistest.begin(ctx)
    actions = [a for a in analysistest.target_actions(env) if a.mnemonic == "Apexer"]
    asserts.true(
        env,
        len(actions) == 1,
        "No apexer action found: %s" % actions,
    )
    argv = actions[0].argv

    asserts.false(
        env,
        "--test_only" in argv,
        "Calling apexer with --test_only when manifest file is specified: %s" % argv,
    )

    actions = [a for a in analysistest.target_actions(env) if a.mnemonic == "MarkAndroidManifestTestOnly"]
    asserts.true(
        env,
        len(actions) == 1,
        "No MarkAndroidManifestTestOnly action found: %s" % actions,
    )
    argv = actions[0].argv

    asserts.true(
        env,
        "--test-only" in argv,
        "Calling manifest_fixer without --test-only: %s" % argv,
    )

    return analysistest.end(env)

apex_testonly_with_manifest_test = analysistest.make(
    _apex_testonly_with_manifest_test_impl,
)

def _test_apex_testonly_with_manifest():
    name = "apex_testonly_with_manifest"
    test_name = name + "_test"

    cc_library_shared(
        name = name + "_lib_cc",
        srcs = [name + "_lib.cc"],
        tags = ["manual"],
        min_sdk_version = "32",
    )

    test_apex(
        name = name,
        native_shared_libs_32 = [name + "_lib_cc"],
        # This will not cause the validation failure because it is testonly.
        min_sdk_version = "30",
        testonly = True,
        tests = [name + "_cc_test"],
        android_manifest = "AndroidManifest.xml",
    )

    # It shouldn't complain about the min_sdk_version of the dep is too low.
    apex_testonly_with_manifest_test(
        name = test_name,
        target_under_test = name,
    )

    return test_name

def _apex_testonly_without_manifest_test_impl(ctx):
    env = analysistest.begin(ctx)
    actions = [a for a in analysistest.target_actions(env) if a.mnemonic == "Apexer"]
    asserts.true(
        env,
        len(actions) == 1,
        "No apexer action found: %s" % actions,
    )
    argv = actions[0].argv[:-1] + actions[0].argv[-1].split(" ")

    asserts.true(
        env,
        "--test_only" in argv,
        "Calling apexer without --test_only when manifest file is not specified: %s" % argv,
    )

    actions = [a for a in analysistest.target_actions(env) if a.mnemonic == "MarkAndroidManifestTestOnly"]
    asserts.true(
        env,
        len(actions) == 0,
        "MarkAndroidManifestTestOnly shouldn't be called when manifest file is not specified: %s" % actions,
    )

    return analysistest.end(env)

apex_testonly_without_manifest_test = analysistest.make(
    _apex_testonly_without_manifest_test_impl,
)

def _test_apex_testonly_without_manifest():
    name = "apex_testonly_without_manifest"
    test_name = name + "_test"

    test_apex(
        name = name,
        testonly = True,
    )

    apex_testonly_without_manifest_test(
        name = test_name,
        target_under_test = name,
    )

    return test_name

def _apex_backing_file_test(ctx):
    env = analysistest.begin(ctx)
    actions = [a for a in analysistest.target_actions(env) if a.mnemonic == "FileWrite" and a.outputs.to_list()[0].basename.endswith("_backing.txt")]
    asserts.true(
        env,
        len(actions) == 1,
        "No FileWrite action found for creating <apex>_backing.txt file: %s" % actions,
    )

    asserts.equals(env, ctx.attr.expected_content, actions[0].content)
    return analysistest.end(env)

apex_backing_file_test = analysistest.make(
    _apex_backing_file_test,
    attrs = {
        "expected_content": attr.string(),
    },
)

def _test_apex_backing_file():
    name = "apex_backing_file"
    test_name = name + "_test"

    cc_library_shared(
        name = name + "_lib_cc",
        srcs = [name + "_lib.cc"],
        tags = ["manual"],
    )

    test_apex(
        name = name,
        native_shared_libs_32 = [name + "_lib_cc"],
        android_manifest = "AndroidManifest.xml",
    )

    apex_backing_file_test(
        name = test_name,
        target_under_test = name,
        expected_content = "apex_backing_file_lib_cc.so libc++.so\n",
    )

    return test_name

def _apex_installed_files_test(ctx):
    env = analysistest.begin(ctx)
    actions = [a for a in analysistest.target_actions(env) if a.mnemonic == "GenerateApexInstalledFileList"]
    asserts.true(
        env,
        len(actions) == 1,
        "No GenerateApexInstalledFileList action found for creating <apex>-installed-files.txt file: %s" % actions,
    )

    asserts.equals(
        env,
        len(ctx.attr.expected_inputs),
        len(actions[0].inputs.to_list()),
        "Expected inputs length: %d, actual inputs length: %d" % (len(ctx.attr.expected_inputs), len(actions[0].inputs.to_list())),
    )
    for file in actions[0].inputs.to_list():
        asserts.true(
            env,
            file.basename in ctx.attr.expected_inputs,
            "Unexpected input: %s" % file.basename,
        )
    asserts.equals(env, ctx.attr.expected_output, actions[0].outputs.to_list()[0].basename)
    return analysistest.end(env)

apex_installed_files_test = analysistest.make(
    _apex_installed_files_test,
    attrs = {
        "expected_inputs": attr.string_list(),
        "expected_output": attr.string(),
    },
)

def _test_apex_installed_files():
    name = "apex_installed_files"
    test_name = name + "_test"

    cc_library_shared(
        name = name + "_lib_cc",
        srcs = [name + "_lib.cc"],
        tags = ["manual"],
    )

    test_apex(
        name = name,
        native_shared_libs_32 = [name + "_lib_cc"],
        android_manifest = "AndroidManifest.xml",
    )

    apex_installed_files_test(
        name = test_name,
        target_under_test = name,
        expected_inputs = ["libc++.so", "apex_installed_files_lib_cc.so"],
        expected_output = "apex_installed_files-installed-files.txt",
    )

    return test_name

def _apex_symbols_used_by_apex_test(ctx):
    env = analysistest.begin(ctx)
    target_under_test = analysistest.target_under_test(env)
    actual = target_under_test[ApexInfo].symbols_used_by_apex

    asserts.equals(env, ctx.attr.expected_path, actual.short_path)

    return analysistest.end(env)

apex_symbols_used_by_apex_test = analysistest.make(
    _apex_symbols_used_by_apex_test,
    attrs = {
        "expected_path": attr.string(),
    },
)

def _test_apex_symbols_used_by_apex():
    name = "apex_with_symbols_used_by_apex"
    test_name = name + "_test"

    test_apex(
        name = name,
    )

    apex_symbols_used_by_apex_test(
        name = test_name,
        target_under_test = name,
        expected_path = "build/bazel/rules/apex/apex_with_symbols_used_by_apex_using.txt",
    )

    return test_name

def _apex_java_symbols_used_by_apex_test(ctx):
    env = analysistest.begin(ctx)
    target_under_test = analysistest.target_under_test(env)
    actual = target_under_test[ApexInfo].java_symbols_used_by_apex

    asserts.equals(env, ctx.attr.expected_path, actual.short_path)

    return analysistest.end(env)

apex_java_symbols_used_by_apex_test = analysistest.make(
    _apex_java_symbols_used_by_apex_test,
    attrs = {
        "expected_path": attr.string(),
    },
)

def _test_apex_java_symbols_used_by_apex():
    name = "apex_with_java_symbols_used_by_apex"
    test_name = name + "_test"

    test_apex(
        name = name,
    )

    apex_java_symbols_used_by_apex_test(
        name = test_name,
        target_under_test = name,
        expected_path = "build/bazel/rules/apex/apex_with_java_symbols_used_by_apex_using.xml",
    )

    return test_name

def _generate_notice_file_test(ctx):
    env = analysistest.begin(ctx)
    actions = [a for a in analysistest.target_actions(env) if a.mnemonic == "GenerateNoticeFile"]
    asserts.true(
        env,
        len(actions) == 1,
        "apex target should have a single GenerateNoticeFile action, found %s" % actions,
    )
    input_json = [f for f in actions[0].inputs.to_list() if f.basename.endswith("_licenses.json")]
    asserts.true(
        env,
        len(input_json) == 1,
        "apex GenerateNoticeFile should have a single input *_license.json file, got %s" % input_json,
    )
    outs = actions[0].outputs.to_list()
    asserts.true(
        env,
        len(outs) == 1 and outs[0].basename == "NOTICE.html.gz",
        "apex GenerateNoticeFile should generate a single NOTICE.html.gz file, got %s" % [o.short_path for o in outs],
    )
    return analysistest.end(env)

apex_generate_notice_file_test = analysistest.make(_generate_notice_file_test)

def _test_apex_generate_notice_file():
    name = "apex_notice_file"
    test_name = name + "_test"
    test_apex(name = name)
    apex_generate_notice_file_test(name = test_name, target_under_test = name)
    return test_name

def _analysis_success_test(ctx):
    env = analysistest.begin(ctx)

    # An empty analysis test that just ensures the target_under_test can be analyzed.
    return analysistest.end(env)

analysis_success_test = analysistest.make(_analysis_success_test)

def _test_apex_available():
    name = "apex_available"
    test_name = name + "_test"
    static_lib_name = name + "_lib_cc_static"
    lib_headers_name = name + "_lib_cc_headers"

    cc_library_static(
        name = static_lib_name,
        srcs = ["src.cc"],
        tags = [
            "manual",
            "apex_available_checked_manual_for_testing",
            # anyapex.
            "apex_available=//apex_available:anyapex",
        ],
    )
    cc_library_headers(
        name = lib_headers_name,
        export_absolute_includes = ["include_dir"],
        tags = [
            "manual",
            "apex_available_checked_manual_for_testing",
            "apex_available=//apex_available:anyapex",
        ],
    )
    cc_library_shared(
        name = name + "_lib_cc",
        srcs = [name + "_lib.cc"],
        deps = [
            static_lib_name,
            lib_headers_name,
        ],
        tags = [
            "manual",
            "apex_available_checked_manual_for_testing",
            # Explicit name.
            "apex_available=" + name,
        ],
    )
    cc_library_shared(
        name = name + "_lib2_cc",
        srcs = [name + "_lib2.cc"],
        tags = [
            "manual",
            "apex_available_checked_manual_for_testing",
            # anyapex.
            "apex_available=//apex_available:anyapex",
        ],
    )
    test_apex(
        name = name,
        native_shared_libs_32 = [
            name + "_lib_cc",
            name + "_lib2_cc",
        ],
        android_manifest = "AndroidManifest.xml",
    )

    analysis_success_test(
        name = test_name,
        target_under_test = name,
    )

    return test_name

def _test_apex_available_failure():
    name = "apex_available_failure"
    test_name = name + "_test"
    static_lib_name = name + "_lib_cc_static"
    lib_headers_name = name + "_lib_cc_headers"

    cc_library_static(
        name = static_lib_name,
        srcs = ["src.cc"],
        tags = [
            "manual",
            "apex_available_checked_manual_for_testing",
        ],
    )
    cc_library_headers(
        name = lib_headers_name,
        export_absolute_includes = ["include_dir"],
        tags = [
            "manual",
            "apex_available_checked_manual_for_testing",
        ],
    )
    cc_library_shared(
        name = name + "_lib_cc",
        srcs = [name + "_lib.cc"],
        deps = [
            static_lib_name,
            lib_headers_name,
        ],
        tags = [
            "manual",
            "apex_available_checked_manual_for_testing",
        ],
    )
    cc_library_shared(
        name = name + "_lib2_cc",
        srcs = [name + "_lib2.cc"],
        tags = [
            "manual",
            "apex_available_checked_manual_for_testing",
            # anyapex.
            "apex_available=//apex_available:anyapex",
        ],
    )
    test_apex(
        name = name,
        native_shared_libs_32 = [
            name + "_lib_cc",
            name + "_lib2_cc",
        ],
        android_manifest = "AndroidManifest.xml",
    )

    expect_failure_test(
        name = test_name,
        target_under_test = name,
        failure_message = """
Error in fail: `@//build/bazel/rules/apex:apex_available_failure` apex has transitive dependencies that do not include the apex in their apex_available tags:
    @//build/bazel/rules/apex:apex_available_failure_lib_cc_static; apex_available tags: []
    @//build/bazel/rules/apex:apex_available_failure_lib_cc_headers; apex_available tags: []
    @//build/bazel/rules/apex:apex_available_failure_lib_cc; apex_available tags: []""",
    )
    return test_name

def _test_apex_available_with_base_apex():
    name = "apex_available_with_base_apex"
    test_name = name + "_test"

    cc_library_shared(
        name = name + "_lib_cc",
        srcs = [name + "_lib.cc"],
        tags = [
            "manual",
            "apex_available_checked_manual_for_testing",
            # Explicit name.
            "apex_available=" + name + "_base",
        ],
    )

    cc_library_shared(
        name = name + "_lib2_cc",
        srcs = [name + "_lib2.cc"],
        tags = [
            "manual",
            "apex_available_checked_manual_for_testing",
            # anyapex.
            "apex_available=//apex_available:anyapex",
        ],
    )

    test_apex(
        name = name,
        native_shared_libs_32 = [
            name + "_lib_cc",
            name + "_lib2_cc",
        ],
        base_apex_name = name + "_base",
        android_manifest = "AndroidManifest.xml",
    )

    analysis_success_test(
        name = test_name,
        target_under_test = name,
    )

    return test_name

def _apex_deps_validation_test_impl(ctx):
    env = analysistest.begin(ctx)

    target_under_test = analysistest.target_under_test(env)
    asserts.new_set_equals(
        env,
        sets.make(ctx.attr.allowed_deps_manifest + ctx.attr._default_apex_deps),
        sets.make(apex_dep_infos_to_allowlist_strings(
            target_under_test[ApexDepsInfo].transitive_deps.to_list(),
        )),
    )

    return analysistest.end(env)

_apex_deps_validation_test = analysistest.make(
    _apex_deps_validation_test_impl,
    attrs = {
        "allowed_deps_manifest": attr.string_list(),
        "_default_apex_deps": attr.string_list(
            default = [
                "libc_llndk_headers(minSdkVersion:apex_inherit)",
                "libc_headers(minSdkVersion:apex_inherit)",
                "libc++abi(minSdkVersion:apex_inherit)",
                "libc++_static(minSdkVersion:apex_inherit)",
                "libc++(minSdkVersion:apex_inherit)",
                "libc++demangle(minSdkVersion:apex_inherit)",
            ],
        ),
    },
    config_settings = {
        "@//build/bazel/rules/apex:unsafe_disable_apex_allowed_deps_check": True,
    },
)

def _test_apex_deps_validation():
    name = "apex_deps_validation"
    test_name = name + "_test"

    aidl_interface_name = name + "_aidl_interface"
    aidl_interface(
        name = aidl_interface_name,
        ndk_config = {
            "enabled": True,
            "min_sdk_version": "28",
        },
        srcs = ["Foo.aidl"],
        tags = [
            "manual",
            "apex_available_checked_manual_for_testing",
            "apex_available=" + name,
            "apex_available=//apex_available:platform",
        ],
    )

    specific_apex_available_name = name + "_specific_apex_available"
    cc_library_shared(
        name = specific_apex_available_name,
        srcs = [name + "_lib.cc"],
        tags = [
            "manual",
            "apex_available_checked_manual_for_testing",
            "apex_available=" + name,
            "apex_available=//apex_available:platform",
        ],
        min_sdk_version = "30",
    )

    any_apex_available_name = name + "_any_apex_available"
    cc_library_shared(
        name = any_apex_available_name,
        srcs = [name + "_lib.cc"],
        implementation_dynamic_deps = [aidl_interface_name + "-V1-ndk"],
        tags = [
            "manual",
            "apex_available_checked_manual_for_testing",
            "apex_available=//apex_available:anyapex",
            "apex_available=//apex_available:platform",
        ],
        min_sdk_version = "30",
    )

    no_platform_available_name = name + "_no_platform_available"
    cc_library_shared(
        name = no_platform_available_name,
        srcs = [name + "_lib.cc"],
        tags = [
            "manual",
            "apex_available_checked_manual_for_testing",
            "apex_available=//apex_available:anyapex",
        ],
        min_sdk_version = "30",
    )

    no_platform_available_transitive_dep_name = name + "_no_platform_available_transitive_dep"
    cc_library_shared(
        name = no_platform_available_transitive_dep_name,
        srcs = [name + "_lib.cc"],
        tags = [
            "manual",
            "apex_available_checked_manual_for_testing",
            "apex_available=//apex_available:anyapex",
        ],
        min_sdk_version = "30",
    )

    platform_available_but_dep_with_no_platform_available_name = name + "_shared_platform_available_but_dep_with_no_platform_available"
    cc_library_shared(
        name = platform_available_but_dep_with_no_platform_available_name,
        srcs = [name + "_lib.cc"],
        deps = [no_platform_available_transitive_dep_name],
        tags = [
            "manual",
            "apex_available_checked_manual_for_testing",
            "apex_available=//apex_available:anyapex",
            "apex_available=//apex_available:platform",
        ],
        min_sdk_version = "30",
    )

    test_apex(
        name = name,
        native_shared_libs_32 = [
            specific_apex_available_name,
            any_apex_available_name,
            no_platform_available_name,
            platform_available_but_dep_with_no_platform_available_name,
        ],
        android_manifest = "AndroidManifest.xml",
        min_sdk_version = "30",
    )

    _apex_deps_validation_test(
        name = test_name,
        target_under_test = name,
        allowed_deps_manifest = [
            specific_apex_available_name + "(minSdkVersion:30)",
            any_apex_available_name + "(minSdkVersion:30)",
            platform_available_but_dep_with_no_platform_available_name + "(minSdkVersion:30)",
            aidl_interface_name + "-V1-ndk(minSdkVersion:28)",
            "jni_headers(minSdkVersion:29)",
        ],
        tags = ["manual"],
    )

    return test_name

_MarchInfo = provider(fields = {"march": "list of march values found in the cc deps of this apex"})

def _apex_transition_test(ctx):
    env = analysistest.begin(ctx)
    target_under_test = analysistest.target_under_test(env)
    march_values = target_under_test[_MarchInfo].march

    asserts.equals(env, ctx.attr.expected, march_values.to_list())

    return analysistest.end(env)

def _cc_compile_test_aspect_impl(target, ctx):
    transitive_march = []
    for attr_deps in get_dep_targets(ctx.rule.attr, predicate = lambda target: _MarchInfo in target).values():
        for dep in attr_deps:
            transitive_march.append(dep[_MarchInfo].march)
    march_values = []
    if (target.label.name).startswith("apex_transition_lib"):
        for a in target.actions:
            if a.mnemonic == "CppCompile":
                march_values += [arg for arg in a.argv if "march" in arg]
    return [
        _MarchInfo(
            march = depset(
                direct = march_values,
                transitive = transitive_march,
            ),
        ),
    ]

_cc_compile_test_aspect = aspect(
    implementation = _cc_compile_test_aspect_impl,
    attr_aspects = ["*"],
)

apex_transition_test = analysistest.make(
    _apex_transition_test,
    attrs = {
        "expected": attr.string_list(),
    },
    extra_target_under_test_aspects = [_cc_compile_test_aspect],
)

def _test_apex_transition():
    name = "apex_transition"
    test_name = name + "_test"

    cc_library_shared(
        name = name + "_lib_cc",
        srcs = [name + "_lib.cc"],
        tags = ["manual"],
    )

    cc_library_shared(
        name = name + "_lib2_cc",
        srcs = [name + "_lib2.cc"],
        tags = ["manual"],
    )

    test_apex(
        name = name,
        native_shared_libs_32 = [name + "_lib_cc"],
        native_shared_libs_64 = [name + "_lib2_cc"],
        android_manifest = "AndroidManifest.xml",
    )

    apex_transition_test(
        name = test_name + "_32",
        target_under_test = name,
        target_compatible_with = ["//build/bazel_common_rules/platforms/os:android", "//build/bazel_common_rules/platforms/arch:arm"],
        expected = ["-march=armv7-a"],
    )

    apex_transition_test(
        name = test_name + "_64",
        target_under_test = name,
        target_compatible_with = ["//build/bazel_common_rules/platforms/os:android", "//build/bazel_common_rules/platforms/arch:arm64"],
        expected = ["-march=armv8-a"],
    )

    return [test_name + "_32", test_name + "_64"]

def _test_no_static_linking_for_stubs_lib():
    name = "no_static_linking_for_stubs_lib"
    test_name = name + "_test"

    cc_library_static(
        name = name + "_static_unavailable_to_apex",
        tags = [
            "apex_available_checked_manual_for_testing",
            "manual",
        ],
    )

    cc_library_shared(
        name = name + "_shared",
        deps = [name + "_static_unavailable_to_apex"],
        tags = [
            "apex_available=" + name,
            "apex_available_checked_manual_for_testing",
            "manual",
        ],
    )

    test_apex(
        name = name,
        native_shared_libs_32 = [name + "_shared"],
    )

    expect_failure_test(
        name = test_name,
        target_under_test = name,
        failure_message = """
Error in fail: `@//build/bazel/rules/apex:no_static_linking_for_stubs_lib` apex has transitive dependencies that do not include the apex in their apex_available tags:
    @//build/bazel/rules/apex:no_static_linking_for_stubs_lib_static_unavailable_to_apex; apex_available tags: []""",
    )

    return test_name

def _test_directly_included_stubs_lib_with_indirectly_static_variant():
    name = "directly_included_stubs_lib_with_indirectly_static_variant"
    test_name = name + "_test"

    cc_binary(
        name = name + "bar",
        deps = [name + "_shared_bp2build_cc_library_static"],
        tags = [
            "apex_available=" + name,
            "apex_available_checked_manual_for_testing",
            "manual",
        ],
    )

    cc_library_shared(
        name = name + "foo",
        deps = [name + "_shared_bp2build_cc_library_static"],
        tags = [
            "apex_available=" + name,
            "apex_available_checked_manual_for_testing",
            "manual",
        ],
    )

    # This target is unavailable to apex but is allowed to be required by
    # cc_binary bar and cc_library_shared foo because its shared variant
    # is directly in the apex
    cc_library_static(
        name = name + "_shared_bp2build_cc_library_static",
        tags = [
            "apex_available_checked_manual_for_testing",
            "manual",
        ],
    )

    cc_library_shared(
        name = name + "_shared",
        tags = [
            "apex_available=" + name,
            "apex_available_checked_manual_for_testing",
            "manual",
        ],
    )

    test_apex(
        name = name,
        native_shared_libs_32 = [name + "_shared", name + "foo"],
        binaries = [name + "bar"],
    )

    target_under_test_exist_test(
        name = test_name,
        target_under_test = name,
    )

    return test_name

def cc_library_shared_with_stubs(name):
    cc_library_shared(
        name = name,
        system_dynamic_deps = [],
        stl = "none",
        tags = ["manual"],
        stubs_symbol_file = name + ".map.txt",
    )

    native.genrule(
        name = name + "_genrule_map_txt",
        outs = [name + ".map.txt"],
        cmd = "touch $@",
        tags = ["manual"],
    )

    cc_stub_suite(
        name = name + "_stub_libs",
        soname = name + ".so",
        source_library_label = ":" + name,
        symbol_file = name + ".map.txt",
        versions = ["30"],
        tags = ["manual"],
    )

    return [
        name,
        name + "_stub_libs",
    ]

def _apex_in_unbundled_build_test(ctx):
    env = analysistest.begin(ctx)
    target_under_test = analysistest.target_under_test(env)
    mk_modules_to_install = target_under_test[ApexMkInfo].make_modules_to_install
    asserts.true(
        env,
        "apex_in_unbundled_build_libfoo" not in mk_modules_to_install,
        "stub libs apex_in_unbundled_build_libfoo should not be propagated " +
        "to make for installation in unbundled mode",
    )
    return analysistest.end(env)

apex_in_unbundled_build_test = analysistest.make(
    _apex_in_unbundled_build_test,
    config_settings = {
        "//command_line_option:platforms": "@//build/bazel/tests/products:aosp_arm64_for_testing_unbundled_build",
    },
)

def _test_apex_in_unbundled_build():
    name = "apex_in_unbundled_build"
    test_name = name + "_test"

    [cc_library_shared_name, cc_stub_suite_name] = cc_library_shared_with_stubs(name + "_libfoo")

    cc_binary(
        name = name + "_bar",
        tags = [
            "apex_available=" + name,
            "apex_available_checked_manual_for_testing",
            "manual",
        ],
        dynamic_deps = select({
            "//build/bazel/rules/apex:android-in_apex": [cc_stub_suite_name + "_current"],
            "//build/bazel/rules/apex:android-non_apex": [cc_library_shared_name],
        }),
    )

    test_apex(
        name = name,
        binaries = [name + "_bar"],
    )

    apex_in_unbundled_build_test(
        name = test_name,
        target_under_test = name,
    )

    return test_name

def _apex_in_bundled_build_test(ctx):
    env = analysistest.begin(ctx)
    target_under_test = analysistest.target_under_test(env)
    mk_modules_to_install = target_under_test[ApexMkInfo].make_modules_to_install
    asserts.true(
        env,
        "apex_in_bundled_build_libfoo" in mk_modules_to_install,
        "stub libs apex_in_unbundled_build_libfoo should be propagated " +
        "to make for installation in unbundled mode",
    )

    return analysistest.end(env)

apex_in_bundled_build_test = analysistest.make(
    _apex_in_bundled_build_test,
    config_settings = {
        "//command_line_option:platforms": "@//build/bazel/tests/products:aosp_arm64_for_testing",
    },
)

def _test_apex_in_bundled_build():
    name = "apex_in_bundled_build"
    test_name = name + "_test"

    [cc_library_shared_name, cc_stub_suite_name] = cc_library_shared_with_stubs(name + "_libfoo")

    cc_binary(
        name = name + "_bar",
        tags = [
            "apex_available=" + name,
            "apex_available_checked_manual_for_testing",
            "manual",
        ],
        dynamic_deps = select({
            "//build/bazel/rules/apex:android-in_apex": [cc_stub_suite_name + "_current"],
            "//build/bazel/rules/apex:android-non_apex": [cc_library_shared_name],
        }),
    )

    test_apex(
        name = name,
        binaries = [name + "_bar"],
    )

    apex_in_bundled_build_test(
        name = test_name,
        target_under_test = name,
    )

    return test_name

def _apex_compression_test(ctx):
    env = analysistest.begin(ctx)

    target = analysistest.target_under_test(env)
    asserts.true(
        env,
        target[ApexInfo].signed_compressed_output != None,
        "ApexInfo.signed_compressed_output should exist from compressible apex",
    )

    return analysistest.end(env)

apex_compression_test = analysistest.make(
    _apex_compression_test,
    config_settings = {
        "//command_line_option:platforms": "@//build/bazel/tests/products:aosp_arm64_for_testing",
    },
)

def _test_apex_compression():
    name = "apex_compression"
    test_name = name + "_test"

    test_apex(
        name = name,
        compressible = True,
    )

    apex_compression_test(
        name = test_name,
        target_under_test = name,
    )

    return test_name

def _apex_no_compression_test(ctx):
    env = analysistest.begin(ctx)

    target = analysistest.target_under_test(env)
    asserts.true(
        env,
        target[ApexInfo].signed_compressed_output == None,
        "ApexInfo.signed_compressed_output should not exist when compression_enabled is not specified",
    )

    return analysistest.end(env)

apex_no_compression_test = analysistest.make(
    _apex_no_compression_test,
    config_settings = {
        "//command_line_option:platforms": "@//build/bazel/tests/products:aosp_arm64_for_testing_no_compression",
    },
)

def _test_apex_no_compression():
    name = "apex_no_compression"
    test_name = name + "_test"

    test_apex(
        name = name,
    )

    apex_no_compression_test(
        name = test_name,
        target_under_test = name,
    )

    return test_name

def _min_target_sdk_version_api_fingerprint_test(ctx):
    env = analysistest.begin(ctx)
    actions = analysistest.target_actions(env)

    apexer_action = None
    for action in actions:
        if action.argv == None:
            continue
        for a in action.argv:
            if "--min_sdk_version" in a:
                apexer_action = action
                break
        if apexer_action != None:
            break

    asserts.true(
        env,
        apexer_action != None,
        "There is no apexer action in all the actions",
    )

    argv = apexer_action.argv[:-1] + apexer_action.argv[-1].split(" ")
    api_fingerprint_in_input = False
    api_fingerprint_path = None
    for f in apexer_action.inputs.to_list():
        if f.basename == "api_fingerprint.txt":
            api_fingerprint_in_input = True
            api_fingerprint_path = f.path
            break

    asserts.true(
        env,
        api_fingerprint_in_input,
        "api_fingerprint.txt is not in the input files",
    )

    expected_target_sdk_version = "123" + ".$(cat {})".format(api_fingerprint_path)
    target_sdk_version_index = argv.index("--target_sdk_version")
    asserts.equals(
        env,
        expected = expected_target_sdk_version,
        actual = argv[target_sdk_version_index + 1] + " " + argv[target_sdk_version_index + 2],
    )

    min_sdk_version_index = argv.index("--min_sdk_version")
    if ctx.attr.min_sdk_version in ["current", "10000"]:
        expected_min_sdk_version = "123" + ".$(cat {})".format(api_fingerprint_path)
        actual_min_sdk_version = argv[min_sdk_version_index + 1] + " " + argv[min_sdk_version_index + 2]
    else:
        expected_min_sdk_version = ctx.attr.min_sdk_version
        actual_min_sdk_version = argv[min_sdk_version_index + 1]
    asserts.equals(
        env,
        expected = expected_min_sdk_version,
        actual = actual_min_sdk_version,
    )

    return analysistest.end(env)

min_target_sdk_version_api_fingerprint_test = analysistest.make(
    _min_target_sdk_version_api_fingerprint_test,
    attrs = {
        "min_sdk_version": attr.string(
            default = "current",
        ),
    },
    config_settings = {
        "//command_line_option:platforms": "@//build/bazel/tests/products:aosp_arm64_for_testing_unbundled_build",
        "@//build/bazel/rules/apex:unbundled_build_target_sdk_with_api_fingerprint": True,
        "@//build/bazel/rules/apex:platform_sdk_codename": "123",
    },
)

def _test_min_target_sdk_version_api_fingerprint_min_sdk_version_specified():
    name = "min_target_sdk_version_api_fingerprint_min_sdk_version_specified"
    test_name = name + "_test"
    min_sdk_version = "30"

    test_apex(
        name = name,
        min_sdk_version = min_sdk_version,
    )

    min_target_sdk_version_api_fingerprint_test(
        name = test_name,
        target_under_test = name,
        min_sdk_version = min_sdk_version,
    )

    return test_name

def _test_min_target_sdk_version_api_fingerprint_min_sdk_version_not_specified():
    name = "min_target_sdk_version_api_fingerprint_min_sdk_version_not_specified"
    test_name = name + "_test"

    test_apex(
        name = name,
    )

    min_target_sdk_version_api_fingerprint_test(
        name = test_name,
        target_under_test = name,
    )

    return test_name

def _apex_sbom_test(ctx):
    env = analysistest.begin(ctx)

    # Action GenerateSBOMMetadata
    actions = [a for a in analysistest.target_actions(env) if a.mnemonic == "GenerateSBOMMetadata"]
    asserts.true(
        env,
        len(actions) == 1,
        "No GenerateSBOMMetadata action found for creating <apex>-sbom-metadata.csv file: %s" % actions,
    )

    input_files = [input.basename for input in actions[0].inputs.to_list()]
    asserts.true(
        env,
        "apex_sbom_lib_cc.so" in input_files,
        "No expected file in inputs of GenerateSBOMMetadata action",
    )

    output_files = [output.basename for output in actions[0].outputs.to_list()]
    asserts.true(
        env,
        "apex_sbom.apex-sbom-metadata.csv" in output_files,
        "No expected file in outputs of GenerateSBOMMetadata action",
    )

    # Action GenerateSBOM
    actions = [a for a in analysistest.target_actions(env) if a.mnemonic == "GenerateSBOM"]
    asserts.true(
        env,
        len(actions) == 1,
        "No GenerateSBOM action found for creating sbom.spdx.json file: %s" % actions,
    )
    input_files = [input.short_path for input in actions[0].inputs.to_list()]
    expected_input_files = [
        "build/bazel/rules/apex/apex_sbom.apex",
        "build/bazel/rules/apex/apex_sbom.apex-sbom-metadata.csv",
        "build/make/tools/sbom/generate-sbom",
        "build/bazel/rules/apex/apex_sbom_lib_cc/apex_sbom_lib_cc.so",
        "build/bazel/rules/apex/METADATA",
    ]
    asserts.true(
        env,
        all([f in input_files for f in expected_input_files]),
        "Missing input files: %s" % input_files,
    )

    output_files = [output.basename for output in actions[0].outputs.to_list()]
    expected_output_files = [
        "apex_sbom.apex.spdx.json",
        "apex_sbom.apex-fragment.spdx",
    ]
    asserts.true(
        env,
        all([f in output_files for f in expected_output_files]),
        "Missing output files: %s" % input_files,
    )

    return analysistest.end(env)

apex_sbom_test = analysistest.make(
    _apex_sbom_test,
)

def _test_apex_sbom():
    name = "apex_sbom"
    test_name = name + "_test"

    cc_library_shared(
        name = name + "_lib_cc",
        srcs = [name + "_lib.cc"],
        tags = ["manual"],
    )

    test_apex(
        name = name,
        native_shared_libs_32 = [name + "_lib_cc"],
        android_manifest = "AndroidManifest.xml",
    )

    apex_sbom_test(
        name = test_name,
        target_under_test = name,
    )

    return test_name

def _test_apex_variant_version():
    name = "apex_variant_version"
    test_name = name + "_test"

    test_apex(
        name = name,
        variant_version = "3",
    )

    expected_manifest_version = default_manifest_version + 3

    action_flags_present_only_for_mnemonic_test(
        name = test_name,
        target_under_test = name,
        mnemonics = ["ApexManifestModify"],
        expected_flags = ["-se", "version", "0", str(expected_manifest_version)],
    )

    return test_name

def apex_test_suite(name):
    native.test_suite(
        name = name,
        tests = [
            _test_canned_fs_config_basic(),
            _test_canned_fs_config_custom(),
            _test_canned_fs_config_binaries(),
            _test_canned_fs_config_native_shared_libs_arm(),
            _test_canned_fs_config_native_shared_libs_arm64(),
            _test_canned_fs_config_prebuilts(),
            _test_canned_fs_config_prebuilts_sort_order(),
            _test_canned_fs_config_runtime_deps(),
            _test_apex_manifest(),
            _test_apex_manifest_min_sdk_version(),
            _test_apex_manifest_min_sdk_version_current(),
            _test_apex_manifest_min_sdk_version_override(),
            _test_apex_manifest_dependencies_nodep(),
            _test_apex_manifest_dependencies_cc_binary_bionic_deps(),
            _test_apex_manifest_dependencies_cc_library_shared_bionic_deps(),
            _test_apex_manifest_dependencies_requires(),
            _test_apex_manifest_dependencies_provides(),
            _test_apex_manifest_dependencies_selfcontained(),
            _test_apex_manifest_dependencies_cc_binary(),
            _test_logging_parent_flag(),
            _test_package_name(),
            _test_package_name_override_from_config(),
            _test_generate_file_contexts(),
            _test_default_apex_manifest_version(),
            _test_override_apex_manifest_version(),
            _test_min_sdk_version_failure(),
            _test_min_sdk_version_failure_transitive(),
            _test_apex_certificate_none(),
            _test_apex_certificate_name(),
            _test_apex_certificate_label(),
            _test_apex_certificate_label_with_overrides(),
            _test_min_sdk_version_apex_inherit(),
            _test_min_sdk_version_apex_inherit_override_min_sdk_tiramisu(),
            _test_apex_testonly_with_manifest(),
            _test_apex_provides_base_zip_files(),
            _test_apex_testonly_without_manifest(),
            _test_apex_backing_file(),
            _test_apex_symbols_used_by_apex(),
            _test_apex_installed_files(),
            _test_apex_java_symbols_used_by_apex(),
            _test_apex_generate_notice_file(),
            _test_apex_available(),
            _test_apex_available_failure(),
            _test_apex_available_with_base_apex(),
            _test_apex_deps_validation(),
            _test_no_static_linking_for_stubs_lib(),
            _test_directly_included_stubs_lib_with_indirectly_static_variant(),
            _test_apex_in_unbundled_build(),
            _test_apex_in_bundled_build(),
            _test_apex_compression(),
            _test_apex_no_compression(),
            _test_min_target_sdk_version_api_fingerprint_min_sdk_version_specified(),
            _test_min_target_sdk_version_api_fingerprint_min_sdk_version_not_specified(),
            _test_apex_sbom(),
            _test_apex_variant_version(),
        ] + _test_apex_transition(),
    )
