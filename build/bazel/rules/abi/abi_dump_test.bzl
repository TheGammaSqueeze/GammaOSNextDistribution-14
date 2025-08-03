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
load("@bazel_skylib//lib:sets.bzl", "sets")
load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts")
load("//build/bazel/rules/cc:cc_library_shared.bzl", "cc_library_shared")
load("//build/bazel/rules/cc:cc_library_static.bzl", "cc_library_static")
load("//build/bazel/rules/test_common:args.bzl", "get_arg_value", "get_arg_values")
load(":abi_dump.bzl", "abi_dump", "find_abi_config")

ABI_LINKER = "prebuilts/clang-tools/linux-x86/bin/header-abi-linker"
ABI_DIFF = "prebuilts/clang-tools/linux-x86/bin/header-abi-diff"

# cxa_demangle.cpp is added as part of the stl in cc_library_shared, so it's dump
# file is always created.
CXA_DEMANGLE = "external/libcxxabi/external/libcxxabi/src/libc++demangle.cxa_demangle.cpp.sdump"
REF_DUMPS_HOME = "build/bazel/rules/abi/abi-dumps"
ARCH = "x86_64"
BITNESS = 64
CONFIG_SETTING_COVERAGE = {
    "//command_line_option:collect_code_coverage": True,
}
CONFIG_SETTING_SKIP_ABI_CHECK = {
    "@//build/bazel/flags/cc/abi:skip_abi_checks": True,
}
CONFIG_SETTING_IN_APEX = {
    "@//build/bazel/rules/apex:within_apex": True,
}

def _abi_linker_action_test_impl(ctx):
    env = analysistest.begin(ctx)
    bin_home = analysistest.target_bin_dir_path(env)
    bazel_out_base = paths.join(bin_home, ctx.label.package)

    actions = analysistest.target_actions(env)
    link_actions = [a for a in actions if a.mnemonic == "AbiLink"]

    asserts.true(
        env,
        len(link_actions) == 1,
        "Abi link action not found: %s" % link_actions,
    )

    action = link_actions[0]

    output_lib_dir = ctx.attr.lib_name + "_stripped"
    stripped_so = paths.join(bazel_out_base, output_lib_dir, "lib" + output_lib_dir + ".so")
    symbol_file = paths.join(ctx.label.package, ctx.attr.symbol_file)
    asserts.set_equals(
        env,
        expected = sets.make(
            [paths.join(bazel_out_base, ctx.label.package, file + ".sdump") for file in ctx.attr.dumps] + [
                ABI_LINKER,
                paths.join(bin_home, CXA_DEMANGLE),
                stripped_so,
                symbol_file,
            ],
        ),
        actual = sets.make([
            file.path
            for file in action.inputs.to_list()
        ]),
    )

    lsdump_file = paths.join(bazel_out_base, ctx.attr.lib_name + ".so.lsdump")
    asserts.set_equals(
        env,
        expected = sets.make([lsdump_file]),
        actual = sets.make([
            file.path
            for file in action.outputs.to_list()
        ]),
    )

    argv = action.argv
    _test_arg_set_correctly(env, argv, "--root-dir", ".")
    _test_arg_set_correctly(env, argv, "-o", lsdump_file)
    _test_arg_set_correctly(env, argv, "-so", stripped_so)
    _test_arg_set_correctly(env, argv, "-arch", ARCH)
    _test_arg_set_correctly(env, argv, "-v", symbol_file)
    _test_arg_set_multi_values_correctly(env, argv, "--exclude-symbol-version", ctx.attr.exclude_symbol_versions)
    _test_arg_set_multi_values_correctly(env, argv, "--exclude-symbol-tag", ctx.attr.exclude_symbol_tags)
    _test_arg_set_multi_values_correctly(
        env,
        argv,
        "-I",
        [paths.join(bazel_out_base, file) for file in ctx.attr.export_includes] +
        [paths.join(ctx.label.package, file) for file in ctx.attr.export_includes] +
        ctx.attr.export_absolute_includes +
        [paths.join(bin_home, file) for file in ctx.attr.export_absolute_includes],
    )

    sdump_files = []
    args = " ".join(argv).split(" ")
    args_len = len(args)

    # The .sdump files are at the end of the args, the abi linker binary is always at index 0.
    for i in reversed(range(args_len)):
        if ".sdump" in args[i]:
            sdump_files.append(args[i])
        else:
            break

    asserts.set_equals(
        env,
        expected = sets.make(
            [paths.join(bazel_out_base, ctx.label.package, file + ".sdump") for file in ctx.attr.dumps] + [
                paths.join(bin_home, CXA_DEMANGLE),
            ],
        ),
        actual = sets.make(sdump_files),
    )

    return analysistest.end(env)

__abi_linker_action_test = analysistest.make(
    impl = _abi_linker_action_test_impl,
    attrs = {
        "dumps": attr.string_list(),
        "lib_name": attr.string(),
        "symbol_file": attr.string(),
        "exclude_symbol_versions": attr.string_list(),
        "exclude_symbol_tags": attr.string_list(),
        "export_includes": attr.string_list(),
        "export_absolute_includes": attr.string_list(),
        "_platform_utils": attr.label(default = Label("//build/bazel/platforms:platform_utils")),
    },
)

def _abi_linker_action_test(**kwargs):
    __abi_linker_action_test(
        target_compatible_with = [
            "//build/bazel_common_rules/platforms/arch:x86_64",
            "//build/bazel_common_rules/platforms/os:android",
        ],
        **kwargs
    )

def _test_abi_linker_action():
    name = "abi_linker_action"
    static_dep_a = name + "_static_dep_a"
    static_dep_b = name + "_static_dep_b"
    test_name = name + "_test"

    cc_library_static(
        name = static_dep_a,
        srcs = ["static_a.cpp"],
        srcs_c = ["static_a.c"],
        export_includes = ["export_includes_static_a"],
        export_absolute_includes = ["export_absolute_includes_static_a"],
        export_system_includes = ["export_system_includes_static_a"],
        local_includes = ["local_includes_static_a"],
        absolute_includes = ["absolute_includes_static_a"],
        tags = ["manual"],
    )

    cc_library_static(
        name = static_dep_b,
        srcs = ["static_b.cpp"],
        srcs_c = ["static_b.c"],
        deps = [":" + static_dep_a],
        export_includes = ["export_includes_static_b"],
        export_absolute_includes = ["export_absolute_includes_static_b"],
        export_system_includes = ["export_system_includes_static_b"],
        local_includes = ["local_includes_static_b"],
        absolute_includes = ["absolute_includes_static_b"],
        tags = ["manual"],
    )

    symbol_file = "shared_a.map.txt"
    exclude_symbol_versions = ["30", "31"]
    exclude_symbol_tags = ["func_1", "func_2"]

    cc_library_shared(
        name = name,
        srcs = ["shared.cpp"],
        srcs_c = ["shared.c"],
        deps = [":" + static_dep_b],
        export_includes = ["export_includes_shared"],
        export_absolute_includes = ["export_absolute_includes_shared"],
        export_system_includes = ["export_system_includes_shared"],
        local_includes = ["local_includes_shared"],
        absolute_includes = ["absolute_includes_shared"],
        stubs_symbol_file = name + ".map.txt",
        abi_checker_symbol_file = symbol_file,
        abi_checker_exclude_symbol_versions = exclude_symbol_versions,
        abi_checker_exclude_symbol_tags = exclude_symbol_tags,
        tags = ["manual"],
    )

    _abi_linker_action_test(
        name = test_name,
        target_under_test = name + "_abi_dump",
        dumps = [
            static_dep_a + ".static_a.cpp",
            static_dep_b + ".static_b.cpp",
            name + "__internal_root.shared.cpp",
            static_dep_a + ".static_a.c",
            static_dep_b + ".static_b.c",
            name + "__internal_root.shared.c",
        ],
        lib_name = name,
        symbol_file = symbol_file,
        exclude_symbol_versions = exclude_symbol_versions,
        exclude_symbol_tags = exclude_symbol_tags,
        export_includes = [
            "export_includes_shared",
            "export_includes_static_a",
            "export_includes_static_b",
        ],
        export_absolute_includes = [
            "export_absolute_includes_shared",
            "export_absolute_includes_static_a",
            "export_absolute_includes_static_b",
        ],
    )

    return test_name

def _abi_linker_action_run_test_impl(ctx):
    env = analysistest.begin(ctx)

    actions = analysistest.target_actions(env)
    link_actions = [a for a in actions if a.mnemonic == "AbiLink"]

    asserts.true(
        env,
        len(link_actions) == 1,
        "Abi link action not found: %s" % link_actions,
    )

    return analysistest.end(env)

__abi_linker_action_run_test = analysistest.make(
    impl = _abi_linker_action_run_test_impl,
)

def _abi_linker_action_run_test(**kwargs):
    __abi_linker_action_run_test(
        target_compatible_with = [
            "//build/bazel_common_rules/platforms/arch:x86_64",
            "//build/bazel_common_rules/platforms/os:android",
        ],
        **kwargs
    )

def _test_abi_linker_action_run_for_enabled():
    name = "abi_linker_action_run_for_enabled"
    test_name = name + "_test"

    cc_library_shared(
        name = name,
        abi_checker_enabled = True,
        tags = ["manual"],
    )

    _abi_linker_action_run_test(
        name = test_name,
        target_under_test = name + "_abi_dump",
    )

    return test_name

def _abi_linker_action_not_run_test_impl(ctx):
    env = analysistest.begin(ctx)

    actions = analysistest.target_actions(env)
    link_actions = [a for a in actions if a.mnemonic == "AbiLink"]

    asserts.true(
        env,
        len(link_actions) == 0,
        "Abi link action found: %s" % link_actions,
    )

    return analysistest.end(env)

__abi_linker_action_not_run_test = analysistest.make(
    impl = _abi_linker_action_not_run_test_impl,
)

def _abi_linker_action_not_run_test(**kwargs):
    __abi_linker_action_not_run_test(
        target_compatible_with = [
            "//build/bazel_common_rules/platforms/arch:x86_64",
            "//build/bazel_common_rules/platforms/os:android",
        ],
        **kwargs
    )

__abi_linker_action_not_run_for_no_device_test = analysistest.make(
    impl = _abi_linker_action_not_run_test_impl,
)

def _abi_linker_action_not_run_for_no_device_test(**kwargs):
    __abi_linker_action_not_run_for_no_device_test(
        target_compatible_with = [
            "//build/bazel_common_rules/platforms/arch:x86_64",
            "//build/bazel_common_rules/platforms/os:linux",
        ],
        **kwargs
    )

__abi_linker_action_not_run_for_coverage_test = analysistest.make(
    impl = _abi_linker_action_not_run_test_impl,
    config_settings = CONFIG_SETTING_COVERAGE,
)

def _abi_linker_action_not_run_for_coverage_test(**kwargs):
    __abi_linker_action_not_run_for_coverage_test(
        target_compatible_with = [
            "//build/bazel_common_rules/platforms/arch:x86_64",
            "//build/bazel_common_rules/platforms/os:android",
        ],
        **kwargs
    )

__abi_linker_action_not_run_if_skipped_test = analysistest.make(
    impl = _abi_linker_action_not_run_test_impl,
    config_settings = CONFIG_SETTING_SKIP_ABI_CHECK,
)

def _abi_linker_action_not_run_if_skipped_test(**kwargs):
    __abi_linker_action_not_run_if_skipped_test(
        target_compatible_with = [
            "//build/bazel_common_rules/platforms/arch:x86_64",
            "//build/bazel_common_rules/platforms/os:android",
        ],
        **kwargs
    )

__abi_linker_action_not_run_apex_no_stubs_test = analysistest.make(
    impl = _abi_linker_action_not_run_test_impl,
    config_settings = CONFIG_SETTING_IN_APEX,
)

def _abi_linker_action_not_run_apex_no_stubs_test(**kwargs):
    __abi_linker_action_not_run_apex_no_stubs_test(
        target_compatible_with = [
            "//build/bazel_common_rules/platforms/arch:x86_64",
            "//build/bazel_common_rules/platforms/os:android",
        ],
        **kwargs
    )

def _test_abi_linker_action_not_run_for_default():
    name = "abi_linker_action_not_run_for_default"
    test_name = name + "_test"

    cc_library_shared(
        name = name,
        tags = ["manual"],
    )

    _abi_linker_action_not_run_test(
        name = test_name,
        target_under_test = name + "_abi_dump",
    )

    return test_name

def _test_abi_linker_action_not_run_for_disabled():
    name = "abi_linker_action_not_run_for_disabled"
    test_name = name + "_test"

    cc_library_shared(
        name = name,
        stubs_symbol_file = name + ".map.txt",
        abi_checker_enabled = False,
        tags = ["manual"],
    )

    _abi_linker_action_not_run_test(
        name = test_name,
        target_under_test = name + "_abi_dump",
    )

    return test_name

def _test_abi_linker_action_not_run_for_no_device():
    name = "abi_linker_action_not_run_for_no_device"
    test_name = name + "_test"

    cc_library_shared(
        name = name,
        abi_checker_enabled = True,
        tags = ["manual"],
    )

    _abi_linker_action_not_run_for_no_device_test(
        name = test_name,
        target_under_test = name + "_abi_dump",
    )

    return test_name

def _test_abi_linker_action_not_run_if_skipped():
    name = "abi_linker_action_not_run_if_skipped"
    test_name = name + "_test"

    cc_library_shared(
        name = name,
        abi_checker_enabled = True,
        tags = ["manual"],
    )

    _abi_linker_action_not_run_if_skipped_test(
        name = test_name,
        target_under_test = name + "_abi_dump",
    )

    return test_name

def _test_abi_linker_action_not_run_for_coverage_enabled():
    name = "abi_linker_action_not_run_for_coverage_enabled"
    test_name = name + "_test"

    cc_library_shared(
        name = name,
        abi_checker_enabled = True,
        features = ["coverage"],
        # Coverage will add an extra lib to all the shared libs, we try to avoid
        # that by clearing the system_dynamic_deps and stl.
        system_dynamic_deps = [],
        stl = "none",
        tags = ["manual"],
    )

    _abi_linker_action_not_run_for_coverage_test(
        name = test_name,
        target_under_test = name + "_abi_dump",
    )

    return test_name

def _test_abi_linker_action_not_run_for_apex_no_stubs():
    name = "abi_linker_action_not_run_for_apex_no_stubs"
    test_name = name + "_test"

    cc_library_shared(
        name = name,
        abi_checker_enabled = True,
        tags = ["manual"],
    )

    _abi_linker_action_not_run_apex_no_stubs_test(
        name = test_name,
        target_under_test = name + "_abi_dump",
    )

    return test_name

def _abi_diff_action_test_impl(ctx):
    env = analysistest.begin(ctx)
    actions = analysistest.target_actions(env)
    diff_actions = [a for a in actions if a.mnemonic == "AbiDiff"]

    asserts.true(
        env,
        len(diff_actions) == 2,
        "There should be two abi diff actions: %s" % diff_actions,
    )

    prev_version, version = find_abi_config(ctx)
    _verify_abi_diff_action(ctx, env, diff_actions[0], prev_version, True)
    _verify_abi_diff_action(ctx, env, diff_actions[1], version, False)

    return analysistest.end(env)

def _verify_abi_diff_action(ctx, env, action, version, is_prev_version):
    bin_home = analysistest.target_bin_dir_path(env)
    bazel_out_base = paths.join(bin_home, ctx.label.package)
    lsdump_file = paths.join(bazel_out_base, ctx.attr.lib_name + ".so.lsdump")

    ref_dump = paths.join(
        REF_DUMPS_HOME,
        "platform",
        str(version),
        str(BITNESS),
        ARCH,
        "source-based",
        ctx.attr.lib_name + ".so.lsdump",
    )
    asserts.set_equals(
        env,
        expected = sets.make([
            lsdump_file,
            ABI_DIFF,
            ref_dump,
        ]),
        actual = sets.make([
            file.path
            for file in action.inputs.to_list()
        ]),
    )

    if is_prev_version:
        diff_file = paths.join(bazel_out_base, ".".join([ctx.attr.lib_name, "so", str(version), "abidiff"]))
    else:
        diff_file = paths.join(bazel_out_base, ".".join([ctx.attr.lib_name, "so", "abidiff"]))

    asserts.set_equals(
        env,
        expected = sets.make([diff_file]),
        actual = sets.make([
            file.path
            for file in action.outputs.to_list()
        ]),
    )

    argv = action.argv
    _test_arg_set_correctly(env, argv, "-o", diff_file)
    _test_arg_set_correctly(env, argv, "-old", ref_dump)
    _test_arg_set_correctly(env, argv, "-new", lsdump_file)
    _test_arg_set_correctly(env, argv, "-lib", ctx.attr.lib_name)
    _test_arg_set_correctly(env, argv, "-arch", ARCH)
    _test_arg_exists(env, argv, "-allow-unreferenced-changes")
    _test_arg_exists(env, argv, "-allow-unreferenced-elf-symbol-changes")
    _test_arg_exists(env, argv, "-allow-extensions")
    if is_prev_version:
        _test_arg_set_correctly(env, argv, "-target-version", str(version + 1))
    else:
        _test_arg_set_correctly(env, argv, "-target-version", "current")

__abi_diff_action_test = analysistest.make(
    impl = _abi_diff_action_test_impl,
    attrs = {
        "lib_name": attr.string(),
        "_platform_utils": attr.label(default = Label("//build/bazel/platforms:platform_utils")),
    },
)

def _abi_diff_action_test(**kwargs):
    __abi_diff_action_test(
        target_compatible_with = [
            "//build/bazel_common_rules/platforms/arch:x86_64",
            "//build/bazel_common_rules/platforms/os:android",
        ],
        **kwargs
    )

def _test_abi_diff_action():
    name = "abi_diff_action"
    test_name = name + "_test"

    cc_library_shared(
        name = name,
        srcs = ["shared.cpp"],
        tags = ["manual"],
    )

    lib_name = "lib" + name
    abi_dump_name = name + "_abi_dump_new"
    abi_dump(
        name = abi_dump_name,
        shared = name + "_stripped",
        root = name + "__internal_root",
        soname = lib_name + ".so",
        enabled = True,
        abi_ref_dumps_platform = "//build/bazel/rules/abi/abi-dumps/platform:bp2build_all_srcs",
        ref_dumps_home = "build/bazel/rules/abi/abi-dumps",
        tags = ["manual"],
    )

    _abi_diff_action_test(
        name = test_name,
        target_under_test = abi_dump_name,
        lib_name = lib_name,
    )

    return test_name

def _abi_diff_action_not_run_test_impl(ctx):
    env = analysistest.begin(ctx)
    actions = analysistest.target_actions(env)
    diff_actions = [a for a in actions if a.mnemonic == "AbiDiff"]

    asserts.true(
        env,
        len(diff_actions) == 0,
        "Abi diff action found: %s" % diff_actions,
    )

    return analysistest.end(env)

__abi_diff_action_not_run_test = analysistest.make(
    impl = _abi_diff_action_not_run_test_impl,
)

def _abi_diff_action_not_run_test(**kwargs):
    __abi_diff_action_not_run_test(
        target_compatible_with = [
            "//build/bazel_common_rules/platforms/arch:x86_64",
            "//build/bazel_common_rules/platforms/os:android",
        ],
        **kwargs
    )

def _test_abi_diff_action_not_run_if_no_ref_dump_found():
    name = "abi_diff_action_not_run_if_no_ref_dump_found"
    test_name = name + "_test"

    cc_library_shared(
        name = name,
        srcs = ["shared.cpp"],
        tags = ["manual"],
    )

    lib_name = "lib" + name
    abi_dump_name = name + "_abi_dump_new"
    abi_dump(
        name = abi_dump_name,
        shared = name + "_stripped",
        root = name + "__internal_root",
        soname = lib_name + ".so",
        enabled = True,
        ref_dumps_home = "build/bazel/rules/abi/abi-dumps",
        tags = ["manual"],
    )

    _abi_diff_action_not_run_test(
        name = test_name,
        target_under_test = abi_dump_name,
    )

    return test_name

def _test_arg_set_correctly(env, argv, arg_name, expected):
    arg = get_arg_value(argv, arg_name)
    asserts.true(
        env,
        arg == expected,
        "%s is not set correctly: expected %s, actual %s" % (arg_name, expected, arg),
    )

def _test_arg_set_multi_values_correctly(env, argv, arg_name, expected):
    args = get_arg_values(argv, arg_name)
    asserts.set_equals(
        env,
        expected = sets.make(expected),
        actual = sets.make(args),
    )

def _test_arg_exists(env, argv, arg_name):
    asserts.true(
        env,
        arg_name in argv,
        "arg %s is not found" % arg_name,
    )

def abi_dump_test_suite(name):
    native.test_suite(
        name = name,
        tests = [
            _test_abi_linker_action(),
            _test_abi_linker_action_not_run_for_default(),
            _test_abi_linker_action_not_run_for_disabled(),
            _test_abi_linker_action_run_for_enabled(),
            _test_abi_linker_action_not_run_for_no_device(),
            _test_abi_linker_action_not_run_for_coverage_enabled(),
            _test_abi_linker_action_not_run_if_skipped(),
            _test_abi_linker_action_not_run_for_apex_no_stubs(),
            _test_abi_diff_action(),
            _test_abi_diff_action_not_run_if_no_ref_dump_found(),
        ],
    )
