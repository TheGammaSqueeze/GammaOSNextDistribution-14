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

"""kt_jvm_library_analysis_test"""

load("//:visibility.bzl", "RULES_KOTLIN")
load("//kotlin/common/testing:analysis.bzl", "kt_analysis")
load("//kotlin/common/testing:asserts.bzl", "kt_asserts")
load("@bazel_skylib//lib:sets.bzl", "sets")
load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts")

visibility(RULES_KOTLIN)

kt_jvm_library_analysis_test = analysistest.make(
    impl = lambda ctx: _kt_jvm_library_analysis_test_impl(ctx),
    attrs = dict(
        expected_al_ruleset_names = attr.string_list(
            doc = "Android Lint rule JARs reported as run on the given target",
            default = kt_analysis.DEFAULT_LIST,
        ),
        expected_compile_jar_names = attr.string_list(
            doc = "Names of all JavaInfo::compile_jars for the given target",
            default = kt_analysis.DEFAULT_LIST,
        ),
        expected_exported_processor_jar_names = attr.string_list(
            doc = "Names of all JavaInfo::plugins JARs returned by the given target",
            default = kt_analysis.DEFAULT_LIST,
        ),
        expected_exported_processor_classes = attr.string_list(
            doc = "Annotation processors reported as to be run on depending targets",
        ),
        expected_processor_classes = attr.string_list(
            doc = "Annotation processors reported as run on the given target",
        ),
        expected_kotlinc_plugin_jar_names = attr.string_list(
            doc = "Names of all -Xplugin= JARs",
            default = kt_analysis.DEFAULT_LIST,
        ),
        expected_friend_jar_names = attr.string_list(
            doc = "Names of all -Xfriend-paths= JARs",
            default = kt_analysis.DEFAULT_LIST,
        ),
        expected_runfile_names = attr.string_list(
            doc = "Names of all runfiles",
            default = kt_analysis.DEFAULT_LIST,
        ),
        expect_jdeps = attr.bool(default = True),
        expect_processor_classpath = attr.bool(),
        expect_neverlink = attr.bool(),
        required_mnemonic_counts = attr.string_dict(
            doc = "Expected mnemonics to expected action count; unlisted mnemonics are ignored",
        ),
    ),
)

def _kt_jvm_library_analysis_test_impl(ctx):
    kt_analysis.check_endswith_test(ctx)

    env = analysistest.begin(ctx)
    actual = ctx.attr.target_under_test

    actions = analysistest.target_actions(env)
    kt_al_action = kt_analysis.get_action(actions, "KtAndroidLint")

    asserts.true(
        env,
        JavaInfo in actual,
        "kt_jvm_library did not produce JavaInfo provider.",
    )
    asserts.true(
        env,
        ProguardSpecProvider in actual,
        "Expected a ProguardSpecProvider provider.",
    )

    if ctx.attr.expected_runfile_names != kt_analysis.DEFAULT_LIST:
        asserts.set_equals(
            env,
            sets.make(ctx.attr.expected_runfile_names),
            sets.make([
                f.basename
                for f in actual[DefaultInfo].data_runfiles.files.to_list()
            ]),
        )

    if ctx.attr.expected_compile_jar_names != kt_analysis.DEFAULT_LIST:
        asserts.set_equals(
            env,
            sets.make(ctx.attr.expected_compile_jar_names),
            sets.make([f.basename for f in actual[JavaInfo].compile_jars.to_list()]),
            "kt_jvm_library JavaInfo::compile_jars",
        )

    if ctx.attr.expected_exported_processor_jar_names != kt_analysis.DEFAULT_LIST:
        asserts.set_equals(
            env,
            sets.make(ctx.attr.expected_exported_processor_jar_names),
            sets.make([f.basename for f in actual[JavaInfo].plugins.processor_jars.to_list()]),
        )

    asserts.set_equals(
        env,
        sets.make(ctx.attr.expected_exported_processor_classes),
        sets.make(actual[JavaInfo].plugins.processor_classes.to_list()),
    )

    kt_2_java_compile = kt_analysis.get_action(actions, "Kt2JavaCompile")

    if kt_2_java_compile:
        asserts.true(
            env,
            kt_2_java_compile.outputs.to_list()[0].basename.endswith(".jar"),
            "Expected first output to be a JAR (this affects the param file name).",
        )

    if ctx.attr.expected_friend_jar_names != kt_analysis.DEFAULT_LIST:
        friend_paths_arg = kt_analysis.get_arg(kt_2_java_compile, "-Xfriend-paths=")
        kt_asserts.list_matches(
            env,
            expected = ["/" + x for x in ctx.attr.expected_friend_jar_names],
            actual = friend_paths_arg.split(",") if friend_paths_arg else [],
            matcher = lambda expected, actual: actual.endswith(expected),
            items_name = "friend JARs",
        )

    if ctx.attr.expected_kotlinc_plugin_jar_names != kt_analysis.DEFAULT_LIST:
        kt_asserts.list_matches(
            env,
            expected = ["/" + x for x in ctx.attr.expected_kotlinc_plugin_jar_names],
            actual = kt_analysis.get_all_args(kt_2_java_compile, "-Xplugin="),
            matcher = lambda expected, actual: actual.endswith(expected),
            items_name = "kotlinc plugin JARs",
        )

    asserts.equals(
        env,
        ctx.attr.expect_neverlink,
        len(actual[JavaInfo].transitive_runtime_jars.to_list()) == 0,
        "Mismatch: Expected transitive_runtime_jars iff (neverlink == False)",
    )

    kt_asserts.required_mnemonic_counts(env, ctx.attr.required_mnemonic_counts, actions)

    return analysistest.end(env)
