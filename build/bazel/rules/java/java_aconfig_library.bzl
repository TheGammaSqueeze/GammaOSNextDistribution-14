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

"""Macro wrapping the java_aconfig_library for bp2build. """

load("@bazel_skylib//lib:paths.bzl", "paths")
load("//build/bazel/rules/aconfig:aconfig_declarations.bzl", "AconfigDeclarationsInfo")
load("//build/bazel/rules/java:sdk_transition.bzl", "sdk_transition")

def _java_aconfig_library_impl(ctx):
    gen_dir_str = paths.join(ctx.label.name, "gen")

    aconfig_declarations = ctx.attr.aconfig_declarations[AconfigDeclarationsInfo]
    gen_srcjar = ctx.actions.declare_file(paths.join(gen_dir_str, ctx.label.name + ".srcjar"))

    # TODO(b/301457407): find a solution for declare_directory.
    gen_srcjar_tmp = ctx.actions.declare_directory("tmp", sibling = gen_srcjar)

    intermediate_path = aconfig_declarations.intermediate_path

    mode = "production"
    if ctx.attr.test:
        mode = "test"

    args = ctx.actions.args()
    args.add("create-java-lib")
    args.add_all(["--mode", mode])
    args.add_all(["--cache", intermediate_path])
    args.add_all(["--out", gen_srcjar_tmp.path])

    ctx.actions.run(
        inputs = [intermediate_path],
        executable = ctx.executable._aconfig,
        outputs = [gen_srcjar_tmp],
        arguments = [args],
        tools = [
            ctx.executable._aconfig,
        ],
        mnemonic = "AconfigCreateJavaLib",
    )

    args = ctx.actions.args()
    args.add("-write_if_changed")
    args.add("-jar")
    args.add("-o", gen_srcjar)
    args.add("-C", gen_srcjar_tmp.path)
    args.add("-D", gen_srcjar_tmp.path)
    args.add("-symlinks=false")

    ctx.actions.run(
        executable = ctx.executable._soong_zip,
        inputs = [gen_srcjar_tmp],
        outputs = [gen_srcjar],
        arguments = [args],
        tools = [
            ctx.executable._soong_zip,
        ],
        mnemonic = "AconfigZipJavaLib",
    )

    out_file = ctx.actions.declare_file(ctx.label.name + ".jar")
    java_info = java_common.compile(
        ctx,
        source_jars = [gen_srcjar],
        deps = [d[JavaInfo] for d in ctx.attr.libs],
        output = out_file,
        java_toolchain = ctx.toolchains["@bazel_tools//tools/jdk:toolchain_type"].java,
    )

    return [
        java_info,
        DefaultInfo(
            files = depset([out_file]),
            runfiles = ctx.runfiles(
                transitive_files = depset(
                    transitive = [java_info.transitive_runtime_jars],
                ),
            ),
        ),
        OutputGroupInfo(
            srcjar = depset([gen_srcjar]),
        ),
    ]

_java_aconfig_library = rule(
    implementation = _java_aconfig_library_impl,
    cfg = sdk_transition,
    attrs = {
        "aconfig_declarations": attr.label(
            providers = [AconfigDeclarationsInfo],
            mandatory = True,
        ),
        "libs": attr.label_list(
            providers = [JavaInfo],
        ),
        "test": attr.bool(default = False),
        "java_version": attr.string(),
        "sdk_version": attr.string(),
        "_aconfig": attr.label(
            allow_single_file = True,
            executable = True,
            cfg = "exec",
            default = Label("//build/make/tools/aconfig:aconfig"),
        ),
        "_soong_zip": attr.label(
            executable = True,
            cfg = "exec",
            allow_single_file = True,
            default = Label("//build/soong/zip/cmd:soong_zip"),
        ),
        "_allowlist_function_transition": attr.label(
            default = "@bazel_tools//tools/allowlists/function_transition_allowlist",
        ),
    },
    toolchains = ["@bazel_tools//tools/jdk:toolchain_type"],
    fragments = ["java"],
    provides = [JavaInfo],
)

def java_aconfig_library(
        name,
        aconfig_declarations,
        test = False,
        sdk_version = "system_current",
        java_version = None,
        visibility = None,
        libs = [],
        tags = [],
        target_compatible_with = []):
    combined_libs = [
        "//frameworks/libs/modules-utils/java:aconfig-annotations-lib",
        "//tools/platform-compat/java/android/compat/annotation:unsupportedappusage",
    ] + libs
    _java_aconfig_library(
        name = name,
        aconfig_declarations = aconfig_declarations,
        libs = combined_libs,
        test = test,
        sdk_version = sdk_version,
        java_version = java_version,
        visibility = visibility,
        tags = tags,
        target_compatible_with = target_compatible_with,
    )

    native.filegroup(
        name = name + ".generated_srcjars",
        srcs = [name],
        output_group = "srcjar",
        visibility = visibility,
        tags = tags,
    )
