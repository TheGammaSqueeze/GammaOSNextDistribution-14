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

"""Compile method that can compile kotlin or java sources"""

load("//:visibility.bzl", "RULES_DEFS_THAT_COMPILE_KOTLIN")
load("//bazel:stubs.bzl", "lint_actions")
load(":common.bzl", "common")
load(":compiler_plugin.bzl", "KtCompilerPluginInfo")
load(":traverse_exports.bzl", "kt_traverse_exports")

visibility(RULES_DEFS_THAT_COMPILE_KOTLIN)

_RULE_FAMILY = common.RULE_FAMILY

def kt_jvm_compile(
        ctx,
        output,
        srcs,
        common_srcs,
        deps,
        plugins,
        runtime_deps,
        exports,
        javacopts,
        kotlincopts,
        neverlink,
        testonly,
        android_lint_plugins,
        resource_files,
        exported_plugins,
        java_android_lint_config = None,
        manifest = None,
        merged_manifest = None,
        classpath_resources = [],
        kt_toolchain = None,
        java_toolchain = None,
        android_lint_rules_jars = depset(),
        disable_lint_checks = [],
        r_java = None,
        output_srcjar = None,
        rule_family = _RULE_FAMILY.UNKNOWN,
        annotation_processor_additional_outputs = [],
        annotation_processor_additional_inputs = [],
        coverage_srcs = [],
        **_kwargs):
    """
    The Kotlin JVM Compile method.

    Args:
      ctx: The context.
      output: A File. The output jar.
      srcs: List of Files. The Kotlin and Java sources.
      common_srcs: List of common source files.
      deps: List of targets. A list of dependencies.
      plugins: List of targets. A list of jvm plugins.
      runtime_deps: List of targets. A list of runtime deps.
      exports: List of targets. A list of exports.
      javacopts: List of strings. A list of Java compile options.
      kotlincopts: List of strings. A list of Kotlin compile options.
      neverlink: A bool. Signifies whether the target is only used for compile-time.
      testonly: A bool. Signifies whether the target is only used for testing only.
      android_lint_plugins: List of targets. An list of android lint plugins to
        execute as a part of linting.
      resource_files: List of Files. The list of Android Resource files.
      exported_plugins: List of exported javac/kotlinc plugins
      java_android_lint_config: Android Lint XML config file to use if there are no Kotlin srcs
      manifest: A File. The raw Android manifest. Optional.
      merged_manifest: A File. The merged Android manifest. Optional.
      classpath_resources: List of Files. The list of classpath resources (kt_jvm_library only).
      kt_toolchain: The Kotlin toolchain.
      java_toolchain: The Java toolchain.
      android_lint_rules_jars: Depset of Files. Standalone Android Lint rule Jar artifacts.
      disable_lint_checks: Whether to disable link checks.
        NOTE: This field should only be used when the provider is not produced
        by a target. For example, the JavaInfo created for the Android R.java
        within an android_library rule.
      r_java: A JavaInfo provider. The JavaInfo provider for the Android R.java
        which is both depended on and propagated as an export.
        NOTE: This field accepts a JavaInfo, but should only be used for the
        Android R.java within an android_library rule.
      output_srcjar: Target output file for generated source jar. Default filename used if None.
      rule_family: The family of the rule calling this function. Element of common.RULE_FAMILY.
        May be used to enable/disable some features.
      annotation_processor_additional_outputs: sequence of Files. A list of
        files produced by an annotation processor.
      annotation_processor_additional_inputs: sequence of Files. A list of
        files consumed by an annotation processor.
      coverage_srcs: Files to use as the basis when computing code coverage. These are typically
        handwritten files that were inputs to generated `srcs`. Should be disjoint with `srcs`.
      **_kwargs: Unused kwargs so that parameters are easy to add and remove.

    Returns:
      A struct that carries the following fields: java_info and validations.
    """
    if rule_family != _RULE_FAMILY.ANDROID_LIBRARY and not (srcs + common_srcs + exports):
        # Demands either of the following to be present.
        # - Source-type artifacts, srcs or common_srcs, including an empty
        #   tree-artifact (a directory) or a srcjar without jar entries.
        # - Exporting targets, exports. It is typically used by a library author
        #   to publish one user-facing target with direct exposure to its
        # dependent libraries.
        fail("Expected one of (srcs, common_srcs, exports) is not empty for kotlin/jvm_compile on target: {}".format(ctx.label))

    if classpath_resources and rule_family != _RULE_FAMILY.JVM_LIBRARY:
        fail("resources attribute only allowed for jvm libraries")

    if type(java_toolchain) == "Target":
        # Allow passing either a target or a provider until all callers are updated
        java_toolchain = java_toolchain[java_common.JavaToolchainInfo]

    java_infos = []

    # The r_java field only support Android resources Jar files. For now, verify
    # that the name of the jar matches "_resources.jar". This check does not to
    # prevent malicious use, the intent is to prevent accidental usage.
    r_java_infos = []
    if r_java:
        for jar in r_java.outputs.jars:
            if not jar.class_jar.path.endswith("_resources.jar"):
                fail("Error, illegal dependency provided for r_java. This " +
                     "only supports Android resource Jar files, " +
                     "'*_resources.jar'.")
        r_java_infos.append(r_java)

    # Skip deps validation check for any android_library target with no kotlin sources: b/239721906
    has_kt_srcs = any([common.is_kt_src(src) for src in srcs])
    if rule_family != _RULE_FAMILY.ANDROID_LIBRARY or has_kt_srcs:
        kt_traverse_exports.expand_forbidden_deps(deps + runtime_deps + exports)

    for dep in deps:
        if JavaInfo in dep:
            java_infos.append(dep[JavaInfo])
        else:
            fail("Unexpected dependency (must provide JavaInfo): %s" % dep.label)

    if kotlincopts != None and "-Werror" in kotlincopts:
        fail("Flag -Werror is not permitted")

    return common.kt_jvm_library(
        ctx,
        classpath_resources = classpath_resources,
        common_srcs = common_srcs,
        coverage_srcs = coverage_srcs,
                deps = r_java_infos + java_infos,
        disable_lint_checks = disable_lint_checks,
        exported_plugins = common.kt_plugins_map(
            java_plugin_infos = common.collect_providers(JavaPluginInfo, exported_plugins),
            android_lint_rulesets = common.collect_providers(lint_actions.AndroidLintRulesetInfo, exported_plugins),
        ),
        # Not all exported targets contain a JavaInfo (e.g. some only have CcInfo)
        exports = r_java_infos + [e[JavaInfo] for e in exports if JavaInfo in e],
        friend_jars = kt_traverse_exports.expand_friend_jars(deps, root = ctx),
        java_toolchain = java_toolchain,
        javacopts = javacopts,
        kotlincopts = kotlincopts,
        compile_jdeps = kt_traverse_exports.expand_direct_jdeps(deps),
        kt_toolchain = kt_toolchain,
        manifest = manifest,
        merged_manifest = merged_manifest,
        native_libraries = [p[CcInfo] for p in deps + runtime_deps + exports if CcInfo in p],
        neverlink = neverlink,
        output = output,
        output_srcjar = output_srcjar,
        plugins = common.kt_plugins_map(
            android_lint_rulesets = (
                common.collect_providers(lint_actions.AndroidLintRulesetInfo, android_lint_plugins) +
                common.collect_providers(lint_actions.AndroidLintRulesetInfo, plugins)
            ) + [
                lint_actions.AndroidLintRulesetInfo(singlejars = android_lint_rules_jars),
            ],
            java_plugin_infos = common.collect_providers(JavaPluginInfo, plugins),
            kt_compiler_plugin_infos = (
                kt_traverse_exports.expand_compiler_plugins(deps).to_list() +
                common.collect_providers(KtCompilerPluginInfo, plugins)
            ),
        ),
        java_android_lint_config = java_android_lint_config,
        resource_files = resource_files,
        runtime_deps = [d[JavaInfo] for d in runtime_deps if JavaInfo in d],
        srcs = srcs,
        testonly = testonly,
        rule_family = rule_family,
        annotation_processor_additional_outputs = annotation_processor_additional_outputs,
        annotation_processor_additional_inputs = annotation_processor_additional_inputs,
    )
