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

"""kt_testing_rules"""

load("//:visibility.bzl", "RULES_KOTLIN")
load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts")
load(":analysis.bzl", "kt_analysis")

visibility(RULES_KOTLIN)

# Mark targets that's aren't expected to build, but are needed for analysis test assertions.
_ONLY_FOR_ANALYSIS_TAGS = ["manual", "nobuilder", "notap"]

def _wrap_for_analysis(inner_rule):
    """Wrap an existing rule to make it easier to use in analysis tests.

    Args:
        inner_rule: [rule|macro]

    Returns:
        [macro] Calls inner_rule with appropate tags, returning the target name
    """

    def wrapper(name, tags = [], **kwargs):
        inner_rule(
            name = name,
            tags = tags + _ONLY_FOR_ANALYSIS_TAGS,
            **kwargs
        )
        return name

    return wrapper

_assert_failure_test = analysistest.make(
    impl = lambda ctx: _assert_failure_test_impl(ctx),
    expect_failure = True,
    attrs = dict(
        msg_contains = attr.string(mandatory = True),
    ),
)

def _assert_failure_test_impl(ctx):
    kt_analysis.check_endswith_test(ctx)

    env = analysistest.begin(ctx)
    asserts.expect_failure(env, ctx.attr.msg_contains)
    return analysistest.end(env)

_coverage_instrumentation_test = analysistest.make(
    impl = lambda ctx: _coverage_instrumentation_test_impl(ctx),
    attrs = dict(
        expected_instrumented_file_names = attr.string_list(),
    ),
    config_settings = {
        "//command_line_option:collect_code_coverage": "1",
        "//command_line_option:instrument_test_targets": "1",
        "//command_line_option:instrumentation_filter": "+",
    },
)

def _coverage_instrumentation_test_impl(ctx):
    env = analysistest.begin(ctx)
    target_under_test = analysistest.target_under_test(env)
    instrumented_files_info = target_under_test[InstrumentedFilesInfo]
    instrumented_files = instrumented_files_info.instrumented_files.to_list()
    asserts.equals(
        env,
        ctx.attr.expected_instrumented_file_names,
        [file.basename for file in instrumented_files],
    )
    return analysistest.end(env)

def _create_file(name, content = ""):
    """Declare a generated file with optional content.

    Args:
        name: [string] The relative file path
        content: [string]

    Returns:
        [File] The label of the file
    """

    if content.startswith("\n"):
        content = content[1:-1]

    native.genrule(
        name = "gen_" + name,
        outs = [name],
        cmd = """
cat > $@ <<EOF
%s
EOF
""" % content,
    )

    return name

_create_dir = rule(
    implementation = lambda ctx: _create_dir_impl(ctx),
    attrs = dict(
        subdir = attr.string(),
        srcs = attr.label_list(allow_files = True),
    ),
)

def _create_dir_impl(ctx):
    dir = ctx.actions.declare_directory(ctx.attr.name)

    command = "mkdir -p {0} " + ("&& cp {1} {0}" if ctx.files.srcs else "# {1}")
    ctx.actions.run_shell(
        command = command.format(
            dir.path + "/" + ctx.attr.subdir,
            " ".join([s.path for s in ctx.files.srcs]),
        ),
        inputs = ctx.files.srcs,
        outputs = [dir],
    )

    return [DefaultInfo(files = depset([dir]))]

kt_testing_rules = struct(
    # go/keep-sorted start
    ONLY_FOR_ANALYSIS_TAGS = _ONLY_FOR_ANALYSIS_TAGS,
    assert_failure_test = _assert_failure_test,
    coverage_instrumentation_test = _coverage_instrumentation_test,
    create_dir = _wrap_for_analysis(_create_dir),
    create_file = _create_file,
    wrap_for_analysis = _wrap_for_analysis,
    # go/keep-sorted end
)
