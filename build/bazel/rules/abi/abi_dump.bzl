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
load("@bazel_skylib//rules:common_settings.bzl", "BuildSettingInfo")
load(
    "@bazel_tools//tools/build_defs/cc:action_names.bzl",
    "CPP_COMPILE_ACTION_NAME",
    "C_COMPILE_ACTION_NAME",
)
load("@soong_injection//api_levels:platform_versions.bzl", "platform_versions")
load("//build/bazel/platforms:platform_utils.bzl", "platforms")
load(
    "//build/bazel/rules/cc:cc_library_common.bzl",
    "build_compilation_flags",
    "get_non_header_srcs",
    "is_bionic_lib",
    "is_bootstrap_lib",
    "parse_apex_sdk_version",
)
load("//build/bazel/rules/cc:cc_library_static.bzl", "CcStaticLibraryInfo")

AbiDumpInfo = provider(fields = ["dump_files"])
AbiDiffInfo = provider(fields = ["diff_files"])

_ABI_CLASS_PLATFORM = "platform"

def _abi_dump_aspect_impl(target, ctx):
    if not _abi_diff_enabled(ctx, ctx.label.name, True):
        return [
            AbiDumpInfo(
                dump_files = depset(),
            ),
        ]

    transitive_dumps = []
    direct_dumps = []

    if CcStaticLibraryInfo in target:
        direct_dumps.extend(_create_abi_dumps(
            ctx,
            target,
            ctx.rule.files.srcs_cpp,
            ctx.rule.attr.copts_cpp,
            CPP_COMPILE_ACTION_NAME,
        ))
        direct_dumps.extend(_create_abi_dumps(
            ctx,
            target,
            ctx.rule.files.srcs_c,
            ctx.rule.attr.copts_c,
            C_COMPILE_ACTION_NAME,
        ))

        for dep in ctx.rule.attr.static_deps:
            if AbiDumpInfo in dep:
                transitive_dumps.append(dep[AbiDumpInfo].dump_files)

    return [
        AbiDumpInfo(
            dump_files = depset(
                direct_dumps,
                transitive = transitive_dumps,
            ),
        ),
    ]

abi_dump_aspect = aspect(
    implementation = _abi_dump_aspect_impl,
    attr_aspects = ["static_deps", "whole_archive_deps"],
    attrs = {
        "_skip_abi_checks": attr.label(
            default = "//build/bazel/flags/cc/abi:skip_abi_checks",
        ),
        # Need this in order to call _abi_diff_enabled in the aspects code.
        "_within_apex": attr.label(
            default = "//build/bazel/rules/apex:within_apex",
        ),
        "_abi_dumper": attr.label(
            allow_files = True,
            executable = True,
            cfg = "exec",
            default = Label("//prebuilts/clang-tools:linux-x86/bin/header-abi-dumper"),
        ),
        "_platform_utils": attr.label(default = Label("//build/bazel/platforms:platform_utils")),
    },
    toolchains = ["@bazel_tools//tools/cpp:toolchain_type"],
    fragments = ["cpp"],
    provides = [AbiDumpInfo],
)

def _create_abi_dumps(ctx, target, srcs, user_flags, action_name):
    dumps = []

    if len(srcs) == 0:
        return dumps

    compilation_context, compilation_flags = build_compilation_flags(
        ctx,
        ctx.rule.attr.roots + ctx.rule.attr.deps + ctx.rule.attr.includes,
        user_flags,
        action_name,
    )
    sources, headers = get_non_header_srcs(srcs)

    header_inputs = (
        headers +
        compilation_context.headers.to_list() +
        compilation_context.direct_headers +
        compilation_context.direct_private_headers +
        compilation_context.direct_public_headers +
        compilation_context.direct_textual_headers
    )
    objects = []
    linker_inputs = target[CcInfo].linking_context.linker_inputs.to_list()

    # These are created in cc_library_static and there should be only one
    # linker_inputs and one libraries
    if CcInfo in target and len(linker_inputs) == 1 and len(linker_inputs[0].libraries) == 1:
        objects = linker_inputs[0].libraries[0].objects
    for file in sources:
        output = _create_abi_dump(ctx, target, file, objects, header_inputs, compilation_flags)
        dumps.append(output)

    return dumps

def _include_flag(flag):
    return ["-I", flag]

def _create_abi_dump(ctx, target, src, objects, header_inputs, compilation_flags):
    """ Utility function to generate abi dump file."""

    file = paths.join(src.dirname, target.label.name + "." + src.basename + ".sdump")
    output = ctx.actions.declare_file(file)
    args = ctx.actions.args()

    args.add("--root-dir", ".")
    args.add("-o", output)
    args.add(src)

    args.add_all(ctx.rule.attr.exports[0][CcInfo].compilation_context.includes.to_list(), map_each = _include_flag)

    args.add("--")
    args.add_all(compilation_flags)

    # The following two args come from here:
    # https://cs.android.com/android/platform/superproject/+/master:build/soong/cc/builder.go;l=247;drc=ba17c7243d0e297efbc6fb5385d6d5aa81db9152
    args.add("-w")

    # TODO(b/254625084): support darwin as well.
    args.add("-isystem", "prebuilts/clang-tools/linux-x86/clang-headers")

    ctx.actions.run(
        inputs = [src] + header_inputs + objects,
        executable = ctx.executable._abi_dumper,
        outputs = [output],
        arguments = [args],
        # TODO(b/186116353): enable sandbox once the bug is fixed.
        execution_requirements = {
            "no-sandbox": "1",
        },
        mnemonic = "AbiDump",
    )

    return output

def create_linked_abi_dump(ctx, dump_files):
    """ Utility function to generate abi dump files."""
    shared_files = ctx.attr.shared[DefaultInfo].files.to_list()
    if len(shared_files) != 1:
        fail("Expected only one shared library file")

    file = ctx.attr.soname + ".lsdump"
    output = ctx.actions.declare_file(file)
    args = ctx.actions.args()

    args.add("--root-dir", ".")
    args.add("-o", output)
    args.add("-so", shared_files[0])
    inputs = dump_files + [shared_files[0]]

    if ctx.file.symbol_file:
        args.add("-v", ctx.file.symbol_file.path)
        inputs.append(ctx.file.symbol_file)
    for v in ctx.attr.exclude_symbol_versions:
        args.add("--exclude-symbol-version", v)
    for t in ctx.attr.exclude_symbol_tags:
        args.add("--exclude-symbol-tag", t)

    args.add("-arch", platforms.get_target_arch(ctx.attr._platform_utils))

    args.add_all(ctx.attr.root[CcInfo].compilation_context.includes.to_list(), map_each = _include_flag)

    args.add_all([d.path for d in dump_files])

    ctx.actions.run(
        inputs = inputs,
        executable = ctx.executable._abi_linker,
        outputs = [output],
        arguments = [args],
        # TODO(b/186116353): enable sandbox once the bug is fixed.
        execution_requirements = {
            "no-sandbox": "1",
        },
        mnemonic = "AbiLink",
    )

    return output

def find_abi_config(_ctx):
    sdk_version = str(platform_versions.platform_sdk_version)
    prev_version = int(parse_apex_sdk_version(sdk_version))
    version = "current"
    if platform_versions.platform_sdk_final:
        prev_version -= 1
        version = sdk_version

    return prev_version, version

def create_abi_diff(ctx, dump_file):
    prev_version, version = find_abi_config(ctx)

    arch = platforms.get_target_arch(ctx.attr._platform_utils)
    bitness = platforms.get_target_bitness(ctx.attr._platform_utils)
    abi_class = _ABI_CLASS_PLATFORM

    # The logic below comes from:
    # https://cs.android.com/android/platform/superproject/+/master:build/soong/cc/library.go;l=1891;drc=c645853ab73ac8c5889b42f4ce7dc9353ee8fd35
    abi_reference_file = None
    if not platform_versions.platform_sdk_final:
        abi_reference_file = _find_abi_ref_file(ctx, prev_version, arch, bitness, abi_class, dump_file.basename)
        if not abi_reference_file:
            prev_version -= 1

    diff_files = []

    # We need to do the abi check for the previous version and current version if the reference
    # abi dump files are available. If the current previous version doesn't have the reference
    # abi dump file we will check against one version earlier.
    if not abi_reference_file:
        abi_reference_file = _find_abi_ref_file(ctx, prev_version, arch, bitness, abi_class, dump_file.basename)
    if abi_reference_file:
        diff_files.append(_run_abi_diff(ctx, arch, prev_version, dump_file, abi_reference_file, True))

    abi_reference_file = _find_abi_ref_file(ctx, version, arch, bitness, abi_class, dump_file.basename)
    if abi_reference_file:
        diff_files.append(_run_abi_diff(ctx, arch, version, dump_file, abi_reference_file, False))

    return diff_files

def _run_abi_diff(ctx, arch, version, dump_file, abi_reference_file, prev_version_diff):
    lib_name = ctx.attr.soname.removesuffix(".so")

    args = ctx.actions.args()

    if ctx.attr.check_all_apis:
        args.add("-check-all-apis")
    else:
        args.add_all(["-allow-unreferenced-changes", "-allow-unreferenced-elf-symbol-changes"])

    if prev_version_diff:
        args.add("-target-version", version + 1)
        diff_file_name = ctx.attr.soname + "." + str(version) + ".abidiff"
    else:
        args.add("-target-version", "current")
        diff_file_name = ctx.attr.soname + ".abidiff"

    args.add("-allow-extensions")

    if len(ctx.attr.diff_flags) > 0:
        args.add_all(ctx.attr.diff_flags)

    args.add("-lib", lib_name)
    args.add("-arch", arch)

    diff_file = ctx.actions.declare_file(diff_file_name)
    args.add("-o", diff_file)
    args.add("-new", dump_file)
    args.add("-old", abi_reference_file)

    ctx.actions.run(
        inputs = [dump_file, abi_reference_file],
        executable = ctx.executable._abi_diff,
        outputs = [diff_file],
        arguments = [args],
        execution_requirements = {
            "no-sandbox": "1",
        },
        mnemonic = "AbiDiff",
    )

    return diff_file

def _find_abi_ref_file(ctx, version, arch, bitness, abi_class, lsdump_name):
    # Currently we only support platform.
    if abi_class == _ABI_CLASS_PLATFORM:
        abi_ref_dumps = ctx.attr.abi_ref_dumps_platform
    else:
        fail("Unsupported ABI class: %s" % abi_class)

    # The expected reference abi dump file
    ref_dump_file = paths.join(
        ctx.attr.ref_dumps_home,
        abi_class,
        str(version),
        str(bitness),
        arch,
        "source-based",
        lsdump_name,
    )

    ref_file = None

    for file in abi_ref_dumps.files.to_list():
        if ref_dump_file == file.path:
            ref_file = file
            break

    return ref_file

def _abi_diff_enabled(ctx, lib_name, is_aspect):
    # The logic here is based on:
    # https://cs.android.com/android/platform/superproject/+/master:build/soong/cc/sabi.go;l=103;drc=cb0ac95bde896fa2aa59193a37ceb580758c322c

    if ctx.attr._skip_abi_checks[BuildSettingInfo].value:
        return False
    if not platforms.is_target_android(ctx.attr._platform_utils):
        return False
    if ctx.coverage_instrumented():
        return False
    if ctx.attr._within_apex[BuildSettingInfo].value:
        if not is_aspect and not ctx.attr.has_stubs:
            return False

        # Logic comes from here:
        # https://cs.android.com/android/platform/superproject/+/master:build/soong/cc/sabi.go;l=158;drc=cb0ac95bde896fa2aa59193a37ceb580758c322c

    elif is_bionic_lib(lib_name) or is_bootstrap_lib(lib_name):
        return False

    # TODO(b/260611960): handle all the other checks in sabi.go
    return True

def _abi_dump_impl(ctx):
    diff_files = depset()
    if _abi_diff_enabled(ctx, ctx.attr.soname.removesuffix(".so"), False) and ctx.attr.root != None:
        dump_files = ctx.attr.root[AbiDumpInfo].dump_files.to_list()
        linked_dump_file = create_linked_abi_dump(ctx, dump_files)
        diff_files = depset(create_abi_diff(ctx, linked_dump_file))

    return ([
        DefaultInfo(files = diff_files),
        AbiDiffInfo(diff_files = diff_files),
    ])

abi_dump = rule(
    implementation = _abi_dump_impl,
    attrs = {
        "shared": attr.label(mandatory = True, providers = [CcSharedLibraryInfo]),
        "root": attr.label(providers = [CcInfo], aspects = [abi_dump_aspect]),
        "soname": attr.string(mandatory = True),
        "has_stubs": attr.bool(default = False),
        "enabled": attr.bool(default = False),
        "explicitly_disabled": attr.bool(default = False),
        "symbol_file": attr.label(allow_single_file = True),
        "exclude_symbol_versions": attr.string_list(default = []),
        "exclude_symbol_tags": attr.string_list(default = []),
        "check_all_apis": attr.bool(default = False),
        "diff_flags": attr.string_list(default = []),
        "abi_ref_dumps_platform": attr.label(default = "//prebuilts/abi-dumps/platform:bp2build_all_srcs"),
        "ref_dumps_home": attr.string(default = "prebuilts/abi-dumps"),
        "_skip_abi_checks": attr.label(
            default = "//build/bazel/flags/cc/abi:skip_abi_checks",
        ),
        "_within_apex": attr.label(
            default = "//build/bazel/rules/apex:within_apex",
        ),
        # TODO(b/254625084): For the following tools we need to support darwin as well.
        "_abi_dumper": attr.label(
            allow_files = True,
            executable = True,
            cfg = "exec",
            default = Label("//prebuilts/clang-tools:linux-x86/bin/header-abi-dumper"),
        ),
        "_abi_linker": attr.label(
            allow_files = True,
            executable = True,
            cfg = "exec",
            default = Label("//prebuilts/clang-tools:linux-x86/bin/header-abi-linker"),
        ),
        "_abi_diff": attr.label(
            allow_files = True,
            executable = True,
            cfg = "exec",
            default = Label("//prebuilts/clang-tools:linux-x86/bin/header-abi-diff"),
        ),
        "_platform_utils": attr.label(default = Label("//build/bazel/platforms:platform_utils")),
    },
    fragments = ["cpp"],
    toolchains = ["@bazel_tools//tools/cpp:toolchain_type"],
)
