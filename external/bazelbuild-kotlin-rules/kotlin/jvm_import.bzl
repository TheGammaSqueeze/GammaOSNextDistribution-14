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

"""Kotlin kt_jvm_import rule."""

load("//:visibility.bzl", "RULES_KOTLIN")
load("//toolchains/kotlin_jvm:java_toolchains.bzl", "java_toolchains")
load("//toolchains/kotlin_jvm:kt_jvm_toolchains.bzl", "kt_jvm_toolchains")
load("@bazel_skylib//lib:dicts.bzl", "dicts")
load(":common.bzl", "common")
load(":compiler_plugin.bzl", "KtCompilerPluginInfo")
load(":traverse_exports.bzl", "kt_traverse_exports")

visibility(RULES_KOTLIN)

def _kt_jvm_import_impl(ctx):
    kt_jvm_toolchain = kt_jvm_toolchains.get(ctx)

    runtime_deps_java_infos = []
    for runtime_dep in ctx.attr.runtime_deps:
        if JavaInfo in runtime_dep:
            runtime_deps_java_infos.append(runtime_dep[JavaInfo])
        elif CcInfo not in runtime_dep:
            fail("Unexpected runtime dependency (must provide JavaInfo or CcInfo): %" % runtime_dep.label)

    result = common.kt_jvm_import(
        ctx,
        kt_toolchain = kt_jvm_toolchain,
        jars = ctx.files.jars,
        srcjar = ctx.file.srcjar,
        deps = common.collect_providers(JavaInfo, ctx.attr.deps),
        runtime_deps = runtime_deps_java_infos,
        neverlink = ctx.attr.neverlink,
        java_toolchain = java_toolchains.get(ctx),
        deps_checker = ctx.executable._deps_checker,
    )

    # Collect runfiles from deps unless neverlink
    runfiles = None
    if not ctx.attr.neverlink:
        transitive_runfiles = []
        for p in common.collect_providers(DefaultInfo, ctx.attr.deps):
            transitive_runfiles.append(p.data_runfiles.files)
            transitive_runfiles.append(p.default_runfiles.files)
        runfiles = ctx.runfiles(
            files = ctx.files.jars,
            transitive_files = depset(transitive = transitive_runfiles),
            collect_default = True,  # handles data attribute
        )

    return [
        result.java_info,
        ProguardSpecProvider(common.collect_proguard_specs(
            ctx,
            ctx.files.proguard_specs,
            ctx.attr.deps,
            kt_jvm_toolchain.proguard_whitelister,
        )),
        OutputGroupInfo(_validation = depset(result.validations)),
        DefaultInfo(runfiles = runfiles),  # rule doesn't build any files
    ]

_KT_JVM_IMPORT_ATTRS = dicts.add(
    java_toolchains.attrs,
    kt_jvm_toolchains.attrs,
    deps = attr.label_list(
        aspects = [kt_traverse_exports.aspect],
        providers = [
            # Each provider-set expands on allow_rules
            [JavaInfo],  # We allow android rule deps to make importing android JARs easier.
        ],
        doc = """The list of libraries this library directly depends on at compile-time. For Java
                 and Kotlin libraries listed, the Jars they build as well as the transitive closure
                 of their `deps` and `exports` will be on the compile-time classpath for this rule;
                 also, the transitive closure of their `deps`, `runtime_deps`, and `exports` will be
                 on the runtime classpath (excluding dependencies only depended on as `neverlink`).

                 Note on strict_deps: any Java type explicitly or implicitly referred to in `srcs`
                 must be included here. This is a stronger requirement than what is enforced for
                 `java_library`. Any build failures resulting from this requirement will include the
                 missing dependencies and a command to fix the rule.""",
    ),
    exported_plugins = attr.label_list(
        providers = [[KtCompilerPluginInfo]],
        cfg = "exec",
        doc = """JVM plugins to export to users.


                 Every plugin listed will run during compliations that depend on this target, as
                 if it were listed directly in that target's `plugins` attribute. `java_*` targets
                 will not run kotlinc plugins""",
    ),
    jars = attr.label_list(
        allow_files = common.JAR_FILE_TYPE,
        allow_empty = False,
        doc = """The list of Java and/or Kotlin JAR files provided to targets that depend on this
                 target (required).  Currently only a single Jar is supported.""",
    ),
    neverlink = attr.bool(
        default = False,
        doc = """Only use this library for compilation and not at runtime.""",
    ),
    proguard_specs = attr.label_list(
        allow_files = True,
        doc = """Proguard specifications to go along with this library.""",
    ),
    runtime_deps = attr.label_list(
        providers = [
            # Each provider-set expands on allow_rules
            [JavaInfo],
            [CcInfo],  # for JNI / native dependencies
        ],
        aspects = [kt_traverse_exports.aspect],
        doc = """Runtime-only dependencies.""",
    ),
    srcjar = attr.label(
        allow_single_file = common.SRCJAR_FILE_TYPES,
        doc = """A JAR file that contains source code for the compiled JAR files.""",
    ),
    _deps_checker = attr.label(
        default = "@bazel_tools//tools/android:aar_import_deps_checker",
        executable = True,
        cfg = "exec",
    ),
)

kt_jvm_import = rule(
    attrs = _KT_JVM_IMPORT_ATTRS,
    fragments = ["java"],
    provides = [JavaInfo],
    implementation = _kt_jvm_import_impl,
    toolchains = [kt_jvm_toolchains.type, "@bazel_tools//tools/jdk:toolchain_type"],
    doc = """Allows the use of precompiled Kotlin `.jar` files as deps of `kt_*` targets.

             Prefer this rule to `java_import` for Kotlin Jars. Most Java-like libraries
             and binaries can depend on this rule, and this rule can in turn depend on Kotlin and
             Java libraries. This rule supports a subset of attributes supported by `java_import`.

             In addition to documentation provided as part of this rule, please also refer to their
             documentation as part of `java_import`.
          """,
)
