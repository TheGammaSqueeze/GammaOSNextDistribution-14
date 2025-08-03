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

"""kt_jvm_compile_stubs"""

load("//:visibility.bzl", "RULES_KOTLIN")
load("//kotlin:common.bzl", "common")
load("//kotlin:jvm_compile.bzl", "kt_jvm_compile")
load("//kotlin:traverse_exports.bzl", "kt_traverse_exports")
load("//kotlin/common/testing:analysis.bzl", "kt_analysis")
load("//kotlin/common/testing:testing_rules.bzl", "kt_testing_rules")
load("//toolchains/kotlin_jvm:java_toolchains.bzl", "java_toolchains")
load("//toolchains/kotlin_jvm:kt_jvm_toolchains.bzl", "kt_jvm_toolchains")
load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts")

visibility(RULES_KOTLIN)

_kt_jvm_compile_stub_rule = rule(
    implementation = lambda ctx: _kt_jvm_compile_stub_rule_impl(ctx),
    attrs = dict(
        srcs = attr.label_list(
            allow_files = True,
        ),
        common_srcs = attr.label_list(
            allow_files = True,
        ),
        deps = attr.label_list(
            aspects = [kt_traverse_exports.aspect],
            providers = [JavaInfo],
        ),
        exports = attr.label_list(
            aspects = [kt_traverse_exports.aspect],
            providers = [JavaInfo],
        ),
        rule_family = attr.int(
            default = common.RULE_FAMILY.UNKNOWN,
        ),
        r_java = attr.label(
            providers = [JavaInfo],
        ),
        _java_toolchain = attr.label(
            default = Label(
                "@bazel_tools//tools/jdk:current_java_toolchain",
            ),
        ),
    ),
    fragments = ["java"],
    outputs = dict(
        jar = "lib%{name}.jar",
    ),
    toolchains = [kt_jvm_toolchains.type, "@bazel_tools//tools/jdk:toolchain_type"],
)

def _kt_jvm_compile_stub_rule_impl(ctx):
    # As additional capabilites need to be tested, this rule should support
    # additional fields/attributes.
    result = kt_jvm_compile(
        ctx,
        output = ctx.outputs.jar,
        srcs = ctx.files.srcs,
        common_srcs = ctx.files.common_srcs,
        deps = ctx.attr.deps,
        plugins = [],
        exported_plugins = [],
        runtime_deps = [],
        exports = ctx.attr.exports,
        javacopts = [],
        kotlincopts = [],
        neverlink = False,
        testonly = False,
                android_lint_plugins = [],
        manifest = None,
        merged_manifest = None,
        resource_files = [],
        rule_family = ctx.attr.rule_family,
        kt_toolchain = kt_jvm_toolchains.get(ctx),
        java_toolchain = java_toolchains.get(ctx),
        disable_lint_checks = [],
        r_java = ctx.attr.r_java[JavaInfo] if ctx.attr.r_java else None,
    )
    return [result.java_info]

_kt_jvm_compile_stub_analysis_test = analysistest.make(
    impl = lambda ctx: _kt_jvm_compile_stub_analysis_test_impl(ctx),
    attrs = dict(
        expected_kotlinc_classpath_names = attr.string_list(default = kt_analysis.DEFAULT_LIST),
    ),
)

def _kt_jvm_compile_stub_analysis_test_impl(ctx):
    kt_analysis.check_endswith_test(ctx)

    env = analysistest.begin(ctx)

    actions = analysistest.target_actions(env)
    kotlinc_action = kt_analysis.get_action(actions, "Kt2JavaCompile")

    asserts.true(
        env,
        JavaInfo in ctx.attr.target_under_test,
        "Did not produce JavaInfo provider.",
    )

    if ctx.attr.expected_kotlinc_classpath_names != kt_analysis.DEFAULT_LIST:
        kotlinc_classpath = kt_analysis.get_arg(kotlinc_action, "-cp", style = "next").split(":")
        asserts.equals(
            env,
            ctx.attr.expected_kotlinc_classpath_names,
            [file.rsplit("/", 1)[1] for file in kotlinc_classpath],
        )

    return analysistest.end(env)

kt_jvm_compile_stubs = struct(
    rule = kt_testing_rules.wrap_for_analysis(_kt_jvm_compile_stub_rule),
    analysis_test = _kt_jvm_compile_stub_analysis_test,
)
