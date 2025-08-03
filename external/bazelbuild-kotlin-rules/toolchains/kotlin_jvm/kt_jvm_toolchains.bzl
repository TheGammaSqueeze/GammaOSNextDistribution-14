# Copyright 2022 Google LLC. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the License);
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

"""Kotlin toolchain."""

load("//:visibility.bzl", "RULES_DEFS_THAT_COMPILE_KOTLIN")
load("@bazel_skylib//rules:common_settings.bzl", "BuildSettingInfo")
load("//bazel:stubs.bzl", "select_java_language_level")
load(":kotlinc_flags.bzl", "kotlinc_flags")

# Work around to toolchains in Google3.
# buildifier: disable=provider-params
KtJvmToolchainInfo = provider()

KT_VERSION = "v1_9_10"

# TODO: Remove this alias. Why are we letting people read this?
KT_LANG_VERSION = kotlinc_flags.KT_LANG_VERSION

# Kotlin JVM toolchain type label
_TYPE = Label("//toolchains/kotlin_jvm:kt_jvm_toolchain_type")

def _opt_for_test(val, getter):
    return getter(val) if val else None

def _kt_jvm_toolchain_impl(ctx):
    profiling_filter = ctx.attr.profiling_filter[BuildSettingInfo].value
    kotlinc_define_flags = kotlinc_flags.read_define_flags(ctx)

    kt_jvm_toolchain = dict(
        # go/keep-sorted start
        android_java8_apis_desugared = ctx.attr.android_java8_apis_desugared,
        android_lint_config = ctx.file.android_lint_config,
        android_lint_runner = ctx.attr.android_lint_runner[DefaultInfo].files_to_run,
        build_marker = ctx.file.build_marker,
        coverage_instrumenter = ctx.attr.coverage_instrumenter[DefaultInfo].files_to_run,
        coverage_runtime = _opt_for_test(ctx.attr.coverage_runtime, lambda x: x[JavaInfo]),
        genclass = ctx.file.genclass,
        header_gen_tool = _opt_for_test(ctx.attr.header_gen_tool, lambda x: x[DefaultInfo].files_to_run),
        is_profiling_enabled = lambda label: profiling_filter and (profiling_filter in str(label)),
        java_language_version = ctx.attr.java_language_version,
        java_runtime = ctx.attr.java_runtime,
        jvm_abi_gen_plugin = ctx.file.jvm_abi_gen_plugin,
        kotlin_compiler = ctx.attr.kotlin_compiler[DefaultInfo].files_to_run,
        kotlin_language_version = ctx.attr.kotlin_language_version,
        kotlin_libs = [x[JavaInfo] for x in ctx.attr.kotlin_libs],
        kotlin_sdk_libraries = ctx.attr.kotlin_sdk_libraries,
        kotlinc_cli_flags = ctx.attr.kotlinc_cli_flags + kotlinc_define_flags,
        kotlinc_ide_flags = ctx.attr.kotlinc_ide_flags + kotlinc_define_flags,
        kt_codegen_java_runtime = ctx.attr.kt_codegen_java_runtime,
        proguard_whitelister = ctx.attr.proguard_whitelister[DefaultInfo].files_to_run,
        source_jar_zipper = ctx.file.source_jar_zipper,
        toolchain_type = None if ctx.attr.toolchain_type == None else str(ctx.attr.toolchain_type.label),
        # go/keep-sorted end
    )
    return [
        platform_common.ToolchainInfo(**kt_jvm_toolchain),
        KtJvmToolchainInfo(**kt_jvm_toolchain),
    ]

kt_jvm_toolchain = rule(
    attrs = dict(
        android_java8_apis_desugared = attr.bool(
            # Reflects a select in build rules.
            doc = "Whether Java 8 API desugaring is enabled",
            mandatory = True,
        ),
        android_lint_config = attr.label(
            cfg = "exec",
            allow_single_file = [".xml"],
        ),
        android_lint_runner = attr.label(
            default = "//bazel:stub_tool",
            executable = True,
            cfg = "exec",
        ),
        build_marker = attr.label(
            default = "//tools:build_marker",
            allow_single_file = [".jar"],
        ),
        coverage_instrumenter = attr.label(
            default = "//tools/coverage:offline_instrument",
            cfg = "exec",
            executable = True,
        ),
        coverage_runtime = attr.label(
            default = "@maven//:org_jacoco_org_jacoco_agent",
        ),
        genclass = attr.label(
            default = "@bazel_tools//tools/jdk:GenClass_deploy.jar",
            cfg = "exec",
            allow_single_file = True,
        ),
        header_gen_tool = attr.label(
            executable = True,
            allow_single_file = True,
            cfg = "exec",
        ),
        java_language_version = attr.string(
            default = "11",
        ),
        java_runtime = attr.label(
            default = "@bazel_tools//tools/jdk:current_java_runtime",
            cfg = "exec",
            allow_files = True,
        ),
        jvm_abi_gen_plugin = attr.label(
            default = "@kotlinc//:jvm_abi_gen_plugin",
            cfg = "exec",
            allow_single_file = [".jar"],
        ),
        kotlin_compiler = attr.label(
            default = "@kotlinc//:kotlin_compiler",
            cfg = "exec",
            executable = True,
        ),
        kotlin_language_version = attr.string(
            default = KT_LANG_VERSION,
        ),
        kotlin_libs = attr.label_list(
            doc = "The libraries required during all Kotlin builds.",
            default = [
                "@kotlinc//:kotlin_stdlib",
                "@kotlinc//:annotations",
            ],
            cfg = "target",
        ),
        kotlin_sdk_libraries = attr.label_list(
            doc = "The libraries required to resolve Kotlin code in an IDE.",
            default = [
                "@kotlinc//:kotlin_reflect",
                "@kotlinc//:kotlin_stdlib",
                "@kotlinc//:kotlin_test_not_testonly",
            ],
            cfg = "target",
        ),
        kotlinc_cli_flags = attr.string_list(
            doc = "The static flags to pass to CLI kotlinc invocations",
            default = kotlinc_flags.CLI_FLAGS,
        ),
        kotlinc_ide_flags = attr.string_list(
            doc = "The static flags to pass to IDE kotlinc invocations",
            default = kotlinc_flags.IDE_FLAGS,
        ),
        kt_codegen_java_runtime = attr.label(
            cfg = "exec",
        ),
        profiling_filter = attr.label(
            default = "//toolchains/kotlin_jvm:profiling_filter",
            providers = [BuildSettingInfo],
        ),
        proguard_whitelister = attr.label(
            default = "@bazel_tools//tools/jdk:proguard_whitelister",
            cfg = "exec",
        ),
        runtime = attr.label_list(
            # This attribute has a "magic" name recognized by the native DexArchiveAspect
            # (b/78647825). Must list all implicit runtime deps here, this is not limited
            # to Kotlin runtime libs.
            default = [
                "@kotlinc//:kotlin_stdlib",
                "@kotlinc//:annotations",
            ],
            cfg = "target",
            doc = "The Kotlin runtime libraries grouped into one attribute.",
        ),
        source_jar_zipper = attr.label(
            default = "//tools/bin:source_jar_zipper_binary",
            cfg = "exec",
            allow_single_file = [".jar"],
        ),
        toolchain_type = attr.label(),
    ),
    provides = [platform_common.ToolchainInfo],
    implementation = _kt_jvm_toolchain_impl,
)

def _declare(
        toolchain_type = _TYPE,
        **kwargs):
    kt_jvm_toolchain(
        android_java8_apis_desugared = select({
            "//conditions:default": False,
        }),
        # The JVM bytecode version to output
        # https://kotlinlang.org/docs/compiler-reference.html#jvm-target-version
        toolchain_type = toolchain_type,
        **kwargs
    )

_ATTRS = dict(
    _toolchain = attr.label(
        # TODO: Delete this attr when fixed.
        doc = "Magic attribute name for DexArchiveAspect (b/78647825)",
        default = "//toolchains/kotlin_jvm:kt_jvm_toolchain_linux_sts_jdk",
    ),
)

kt_jvm_toolchains = struct(
    name = _TYPE.name,
    get = lambda ctx: ctx.toolchains[_TYPE],
    type = str(_TYPE),
    declare = _declare,
    attrs = _ATTRS,
)
