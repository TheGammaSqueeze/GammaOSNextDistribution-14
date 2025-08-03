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

load("@rules_android//rules:common.bzl", _common = "common")
load("@rules_android//rules:java.bzl", _java = "java")
load(
    "@rules_android//rules:processing_pipeline.bzl",
    "ProviderInfo",
    "processing_pipeline",
)
load("@rules_android//rules:utils.bzl", "utils")
load(
    "@rules_android//rules/android_library:impl.bzl",
    "finalize",
    _BASE_PROCESSORS = "PROCESSORS",
)
load("@rules_kotlin//kotlin:common.bzl", _kt_common = "common")
load("@rules_kotlin//kotlin:compiler_opt.bzl", "merge_kotlincopts")
load("@rules_kotlin//kotlin:jvm_compile.bzl", "kt_jvm_compile")
load("@rules_kotlin//toolchains/kotlin_jvm:kt_jvm_toolchains.bzl", _kt_jvm_toolchains = "kt_jvm_toolchains")

def _validations_processor(ctx, **_unused_sub_ctxs):
    utils.check_for_failures(ctx.label, ctx.attr.deps, ctx.attr.exports)

def _process_jvm(
        ctx,
        java_package,  # @unused
        exceptions_ctx,  # @unused
        resources_ctx,
        idl_ctx,
        db_ctx,
        **_unused_sub_ctxs):
    # Filter out disallowed sources.
    srcs = ctx.files.srcs + idl_ctx.idl_java_srcs + db_ctx.java_srcs

    # kt_jvm_compile expects deps that only carry CcInfo in runtime_deps
    deps = [dep for dep in ctx.attr.deps if JavaInfo in dep] + idl_ctx.idl_deps
    runtime_deps = [dep for dep in ctx.attr.deps if JavaInfo not in dep]

    jvm_ctx = kt_jvm_compile(
        ctx,
        ctx.outputs.lib_jar,
        # ctx.outputs.lib_src_jar,  # Implicitly determines file.
        srcs = srcs,
        common_srcs = ctx.files.common_srcs,
        coverage_srcs = ctx.files.coverage_srcs,
        deps = deps,
        plugins = ctx.attr.plugins + db_ctx.java_plugins,
        exports = ctx.attr.exports,
        # As the JavaInfo constructor does not support attaching
        # exported_plugins, for the purposes of propagation, the plugin is
        # wrapped in a java_library.exported_plugins target and attached with
        # export to this rule.
        exported_plugins = ctx.attr.exported_plugins,
        runtime_deps = runtime_deps,
        r_java = resources_ctx.r_java,
        javacopts = ctx.attr.javacopts + db_ctx.javac_opts,
        kotlincopts = merge_kotlincopts(ctx),
        neverlink = ctx.attr.neverlink,
        testonly = ctx.attr.testonly,
        android_lint_plugins = [],
        android_lint_rules_jars = depset(),
        manifest = getattr(ctx.file, "manifest", None),
        merged_manifest = resources_ctx.merged_manifest,
        resource_files = ctx.files.resource_files,
        kt_toolchain = _kt_jvm_toolchains.get(ctx),
        java_toolchain = _common.get_java_toolchain(ctx),
        disable_lint_checks = [],
        rule_family = _kt_common.RULE_FAMILY.ANDROID_LIBRARY,
        annotation_processor_additional_outputs = (
            db_ctx.java_annotation_processor_additional_outputs
        ),
        annotation_processor_additional_inputs = (
            db_ctx.java_annotation_processor_additional_inputs
        ),
    )

    java_info = jvm_ctx.java_info

    return ProviderInfo(
        name = "jvm_ctx",
        value = struct(
            java_info = java_info,
            providers = [java_info],
        ),
    )

def _process_coverage(ctx, **_unused_ctx):
    return ProviderInfo(
        name = "coverage_ctx",
        value = struct(
            providers = [
                coverage_common.instrumented_files_info(
                    ctx,
                    source_attributes = ["srcs", "coverage_srcs"],
                    dependency_attributes = ["assets", "deps", "exports"],
                ),
            ],
        ),
    )

PROCESSORS = processing_pipeline.prepend(
    processing_pipeline.replace(
        _BASE_PROCESSORS,
        JvmProcessor = _process_jvm,
        CoverageProcessor = _process_coverage,
    ),
    ValidationsProcessor = _validations_processor,
)

_PROCESSING_PIPELINE = processing_pipeline.make_processing_pipeline(
    processors = PROCESSORS,
    finalize = finalize,
)

def impl(ctx):
    java_package = _java.resolve_package_from_label(ctx.label, ctx.attr.custom_package)
    return processing_pipeline.run(ctx, java_package, _PROCESSING_PIPELINE)
