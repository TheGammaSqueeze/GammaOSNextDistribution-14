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
load("@bazel_tools//tools/cpp:toolchain_utils.bzl", "find_cpp_toolchain")

def _get_clang_cmd_output(ctx):
    copts = [
        "--target=bpf",
        "-nostdlibinc",
        "-no-canonical-prefixes",
        "-O2",
    ]
    copts.extend(ctx.attr.copts)
    if ctx.attr.btf:
        copts.append("-g")

    includes = [
        "packages/modules/Connectivity/staticlibs/native/bpf_headers/include/bpf",
        # TODO(b/149785767): only give access to specific file with AID_* constants
        "system/core/libcutils/include",
        "external/musl/src/env",
        ctx.label.package,
    ]
    includes.extend(ctx.attr.absolute_includes)

    system_includes = [
        "bionic/libc/include",
        "bionic/libc/kernel/uapi",
        # The architecture doesn't matter here, but asm/types.h is included by linux/types.h.
        "bionic/libc/kernel/uapi/asm-arm64",
        "bionic/libc/kernel/android/uapi",
    ]

    toolchain = find_cpp_toolchain(ctx)
    extra_features = [
        "dependency_file",
        "bpf_compiler_flags",
    ]
    extra_disabled_features = [
        "sdk_version_flag",
        "pie",
        "non_external_compiler_flags",
        "common_compiler_flags",
        "asm_compiler_flags",
        "cpp_compiler_flags",
        "c_compiler_flags",
        "external_compiler_flags",
        "arm_isa_arm",
        "arm_isa_thumb",
        "no_override_clang_global_copts",
    ]
    feature_configuration = cc_common.configure_features(
        ctx = ctx,
        cc_toolchain = toolchain,
        requested_features = ctx.features + extra_features,
        unsupported_features = ctx.disabled_features + extra_disabled_features,
    )

    compilation_context = []
    dir_name = ctx.label.name
    if ctx.attr.btf:
        # If btf is true, intermediate dir ("unstripped") is added when
        # clang command is executed, because ctx.actions.run used in stripped
        # command does not allow the same input and output names.
        # "unstripped" will be removed when strip command is executed.
        dir_name = paths.join("unstripped", dir_name)
    (compilation_context, compilation_outputs) = cc_common.compile(
        name = dir_name,
        actions = ctx.actions,
        feature_configuration = feature_configuration,
        cc_toolchain = toolchain,
        srcs = ctx.files.srcs,
        system_includes = system_includes,
        includes = includes,
        user_compile_flags = copts,
        compilation_contexts = compilation_context,
    )

    return compilation_outputs.objects

def _declare_stripped_cmd_output_file(ctx, src):
    file_path = paths.join("_objs", src.basename, src.basename)
    return ctx.actions.declare_file(file_path)

def _get_stripped_cmd_output(ctx, srcs):
    out_files = [_declare_stripped_cmd_output_file(ctx, src) for src in srcs]

    args = ctx.actions.args()
    args.add("--strip-unneeded")
    args.add("--remove-section=.rel.BTF")
    args.add("--remove-section=.rel.BTF.ext")
    args.add("--remove-section=.BTF.ext")

    for in_file, out_file in zip(srcs, out_files):
        ctx.actions.run(
            inputs = [in_file],
            outputs = [out_file],
            executable = ctx.executable._strip,
            arguments = [args] + [in_file.path, "-o", out_file.path],
        )

    return out_files

def _bpf_impl(ctx):
    for src in ctx.files.srcs:
        if "_" in src.basename:
            fail("Invalid character '_' in source name")

    clang_outfiles = _get_clang_cmd_output(ctx)

    if not ctx.attr.btf:
        return [DefaultInfo(files = depset(clang_outfiles))]
    else:
        stripped_outfiles = _get_stripped_cmd_output(ctx, clang_outfiles)
        return [DefaultInfo(files = depset(stripped_outfiles))]

bpf = rule(
    implementation = _bpf_impl,
    attrs = {
        "srcs": attr.label_list(
            mandatory = True,
            allow_files = True,
        ),
        "copts": attr.string_list(),
        "absolute_includes": attr.string_list(),
        "btf": attr.bool(
            default = True,
            doc = "if set to true, generate BTF debug info for maps & programs",
        ),
        "_strip": attr.label(
            cfg = "exec",
            executable = True,
            default = "//prebuilts/clang/host/linux-x86:llvm-strip",
            allow_files = True,
        ),
    },
    toolchains = ["@bazel_tools//tools/cpp:toolchain_type"],
    fragments = ["cpp"],
)
