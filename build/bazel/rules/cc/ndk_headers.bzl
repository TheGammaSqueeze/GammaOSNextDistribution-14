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

"""
`ndk_headers` provides a CcInfo for building SDK variants of CC libraries.
Unlike cc_library_headers, it has a `from` and `to` attribute that can be used to create an import path for headers that
is different than the checked-in folder layout.
"""

load("@bazel_skylib//lib:paths.bzl", "paths")

_VERSIONER_DEPS_DIR = "bionic/libc/versioner-dependencies"

# Creates an action to copy NDK headers to out/ after handling {strip}_import_prefix
# Return a tuple with the following elements
# 1. root of the synthetic dir (this will become -isystem/-I)
# 2. list of hdr files in out
def _assemeble_headers(ctx):
    out_dir = paths.join(
        ctx.bin_dir.path,
        ctx.label.package,
        ctx.label.name,
    )

    outs = []
    for hdr in ctx.files.hdrs:
        rel_to_package = paths.relativize(
            hdr.path,
            ctx.label.package,
        )
        rel_after_strip_import = paths.relativize(
            rel_to_package,
            ctx.attr.strip_import_prefix,
        )
        out = ctx.actions.declare_file(
            paths.join(
                ctx.label.name,
                ctx.attr.import_prefix,
                rel_after_strip_import,
            ),
        )

        ctx.actions.run_shell(
            inputs = depset(direct = [hdr]),
            outputs = [out],
            command = "cp -f %s %s" % (hdr.path, out.path),
            mnemonic = "CopyFile",
            use_default_shell_env = True,
        )
        outs.append(out)

    return out_dir, outs

# Creates an action to run versioner on the assembled NDK headers
# Used for libc
# Return a tuple with the following elements
# 1. root of the synthetic dir (this will become -isystem/-I)
# 2. list of hdr files in out
def _version_headers(ctx, assembled_out_dir, assembled_hdrs):
    out_dir = assembled_out_dir + ".versioned"
    outs = []

    for assembled_hdr in assembled_hdrs:
        rel = paths.relativize(
            assembled_hdr.path,
            assembled_out_dir,
        )
        out = ctx.actions.declare_file(
            paths.join(
                ctx.label.name + ".versioned",
                rel,
            ),
        )
        outs.append(out)

    args = ctx.actions.args()
    args.add_all(["-o", out_dir, assembled_out_dir, _VERSIONER_DEPS_DIR])

    ctx.actions.run(
        executable = ctx.executable._versioner,
        arguments = [args],
        inputs = assembled_hdrs,
        outputs = outs,
        mnemonic = "VersionBionicHeaders",
        tools = ctx.files._versioner_include_deps,
    )

    return out_dir, outs

# Keep in sync with
# https://cs.android.com/android/_/android/platform/build/soong/+/main:cc/config/toolchain.go;l=120-127;drc=1717b3bb7a49c52b26c90469f331b55f7b681690;bpv=1;bpt=0
def _ndk_triple(ctx):
    if ctx.target_platform_has_constraint(ctx.attr._arm_constraint[platform_common.ConstraintValueInfo]):
        return "arm-linux-androideabi"
    if ctx.target_platform_has_constraint(ctx.attr._arm64_constraint[platform_common.ConstraintValueInfo]):
        return "aarch64-linux-android"
    if ctx.target_platform_has_constraint(ctx.attr._riscv64_constraint[platform_common.ConstraintValueInfo]):
        return "riscv64-linux-android"
    if ctx.target_platform_has_constraint(ctx.attr._x86_constraint[platform_common.ConstraintValueInfo]):
        return "i686-linux-android"
    if ctx.target_platform_has_constraint(ctx.attr._x86_64_constraint[platform_common.ConstraintValueInfo]):
        return "x86_64-linux-android"
    fail("Could not determine NDK triple: unrecognized arch")

def _ndk_headers_impl(ctx):
    # Copy the hdrs to a synthetic root
    out_dir, outs = _assemeble_headers(ctx)
    if ctx.attr.run_versioner:
        # Version the copied headers
        out_dir, outs = _version_headers(ctx, out_dir, outs)

    compilation_context = cc_common.create_compilation_context(
        headers = depset(outs),
        # ndk_headers are provided as -isystem and not -I
        # https://cs.android.com/android/_/android/platform/build/soong/+/main:cc/compiler.go;l=394-403;drc=e0202c4823d1b3cabf63206d3a6611868d1559e1;bpv=1;bpt=0
        system_includes = depset([
            out_dir,
            paths.join(out_dir, _ndk_triple(ctx)),
        ]),
    )
    return [
        DefaultInfo(files = depset(outs)),
        CcInfo(compilation_context = compilation_context),
    ]

ndk_headers = rule(
    implementation = _ndk_headers_impl,
    attrs = {
        "strip_import_prefix": attr.string(
            doc =
                """
            The prefix to strip from the .h files of this target.
            e.g if the hdrs are `dir/foo.h` and strip_import_prefix is `dir`, then rdeps will include it as #include <foo.h>
            """,
            default = "",
        ),
        "import_prefix": attr.string(
            doc =
                """
            The prefix to add to the .h files of this target.
            e.g if the hdrs are `dir/foo.h` and import_prefix is `dir_prefix`, then rdeps will include it as #include <dir_prefix/dir/foo.h>
            """,
            default = "",
        ),
        "hdrs": attr.label_list(
            doc = ".h files contributed by the library to the Public API surface (NDK)",
            allow_files = True,
        ),
        "run_versioner": attr.bool(
            doc = "Run versioner with bionic/libc/versioner-dependencies on the include path. Used only by libc",
            default = False,
        ),
        "_versioner": attr.label(
            executable = True,
            cfg = "exec",
            default = Label("//prebuilts/clang-tools:versioner"),
        ),
        "_versioner_include_deps": attr.label(
            doc = "Filegroup containing the .h files placed on the include path when running versioner",
            default = Label("//bionic/libc:versioner-dependencies"),
        ),
        "_arm_constraint": attr.label(
            default = Label("//build/bazel_common_rules/platforms/arch:arm"),
        ),
        "_arm64_constraint": attr.label(
            default = Label("//build/bazel_common_rules/platforms/arch:arm64"),
        ),
        "_riscv64_constraint": attr.label(
            default = Label("//build/bazel_common_rules/platforms/arch:riscv64"),
        ),
        "_x86_constraint": attr.label(
            default = Label("//build/bazel_common_rules/platforms/arch:x86"),
        ),
        "_x86_64_constraint": attr.label(
            default = Label("//build/bazel_common_rules/platforms/arch:x86_64"),
        ),
    },
    provides = [CcInfo],
)
