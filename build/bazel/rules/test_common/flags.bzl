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
load("@bazel_skylib//lib:sets.bzl", "sets")
load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts")

def _assert_flags_present_in_action(env, action, expected_flags):
    if action.argv == None:
        asserts.true(
            env,
            False,
            "expected %s action to have arguments, but argv was None" % (
                action.mnemonic,
            ),
        )
        return
    for flag in expected_flags:
        asserts.true(
            env,
            flag in action.argv,
            "%s action did not contain flag %s; argv: %s" % (
                action.mnemonic,
                flag,
                action.argv,
            ),
        )

# Checks for the presence of a set of given flags in a set of given actions
# non-exclusively. In other words, it confirms that the specified actions
# contain the given flags, but does not confirm that other actions do not
# contain them.
def _action_flags_present_for_mnemonic_nonexclusive_test_impl(ctx):
    env = analysistest.begin(ctx)

    for action in analysistest.target_actions(env):
        if action.mnemonic in ctx.attr.mnemonics:
            _assert_flags_present_in_action(
                env,
                action,
                ctx.attr.expected_flags,
            )

    return analysistest.end(env)

action_flags_present_for_mnemonic_nonexclusive_test = analysistest.make(
    _action_flags_present_for_mnemonic_nonexclusive_test_impl,
    attrs = {
        "mnemonics": attr.string_list(
            doc = """
            Actions with these mnemonics will be expected to have the flags
            specified in expected_flags
            """,
        ),
        "expected_flags": attr.string_list(doc = "The flags to be checked for"),
    },
)

# Checks for the presence of a set of given flags in a set of given actions
# exclusively. In other words, it confirms that *only* the specified actions
# contain the specified flags.
def _action_flags_present_only_for_mnemonic_test_impl(ctx):
    env = analysistest.begin(ctx)

    actions = analysistest.target_actions(env)
    found_at_least_one_action = False
    for action in actions:
        if action.mnemonic in ctx.attr.mnemonics:
            found_at_least_one_action = True
            _assert_flags_present_in_action(
                env,
                action,
                ctx.attr.expected_flags,
            )
        elif action.argv != None:
            for flag in ctx.attr.expected_flags:
                asserts.false(
                    env,
                    flag in action.argv,
                    "%s action unexpectedly contained flag %s; argv: %s" % (
                        action.mnemonic,
                        flag,
                        action.argv,
                    ),
                )
    asserts.true(
        env,
        found_at_least_one_action,
        "did not find any actions with mnemonic %s, found: %s" % (
            ctx.attr.mnemonics,
            [a.mnemonic for a in actions],
        ),
    )
    return analysistest.end(env)

def action_flags_present_only_for_mnemonic_test_with_config_settings(config_settings = {}):
    return analysistest.make(
        _action_flags_present_only_for_mnemonic_test_impl,
        attrs = {
            "mnemonics": attr.string_list(
                doc = """
                Actions with these mnemonics will be expected to have the flags
                specified in expected_flags
                """,
            ),
            "expected_flags": attr.string_list(doc = "The flags to be checked for"),
        },
        config_settings = config_settings,
    )

action_flags_present_only_for_mnemonic_test = action_flags_present_only_for_mnemonic_test_with_config_settings()

action_flags_present_only_for_mnemonic_aosp_arm64_test = action_flags_present_only_for_mnemonic_test_with_config_settings({
    "//command_line_option:platforms": "@//build/bazel/tests/products:aosp_arm64_for_testing",
})

action_flags_present_only_for_mnemonic_aosp_arm64_host_test = action_flags_present_only_for_mnemonic_test_with_config_settings({
    "//command_line_option:platforms": "@//build/bazel/tests/products:aosp_arm64_for_testing_linux_x86_64",
})

# Checks that a given set of flags are NOT present in a given set of actions.
# Unlike the above test, this test does NOT confirm the absence of flags
# *exclusively*. It does not confirm that the flags are present in actions
# other than those specified
def _action_flags_absent_for_mnemonic_test_impl(ctx):
    env = analysistest.begin(ctx)

    actions = analysistest.target_actions(env)
    for action in actions:
        if action.mnemonic in ctx.attr.mnemonics and action.argv != None:
            for flag in ctx.attr.expected_absent_flags:
                asserts.false(
                    env,
                    flag in action.argv,
                    "%s action unexpectedly contained flag %s; argv: %s" % (
                        action.mnemonic,
                        flag,
                        action.argv,
                    ),
                )

    return analysistest.end(env)

def action_flags_absent_for_mnemonic_test_with_config_settings(config_settings = {}):
    return analysistest.make(
        _action_flags_absent_for_mnemonic_test_impl,
        attrs = {
            "mnemonics": attr.string_list(
                doc = """
                Actions with these mnemonics will be expected NOT to have the flags
                specificed in expected_flags
                """,
            ),
            "expected_absent_flags": attr.string_list(
                doc = """
                The flags to be confirmed are absent from the actions in mnemonics
                """,
            ),
        },
        config_settings = config_settings,
    )

action_flags_absent_for_mnemonic_test = action_flags_absent_for_mnemonic_test_with_config_settings()

action_flags_absent_for_mnemonic_aosp_arm64_test = action_flags_absent_for_mnemonic_test_with_config_settings({
    "//command_line_option:platforms": "@//build/bazel/tests/products:aosp_arm64_for_testing",
})

action_flags_absent_for_mnemonic_aosp_arm64_host_test = action_flags_absent_for_mnemonic_test_with_config_settings({
    "//command_line_option:platforms": "@//build/bazel/tests/products:aosp_arm64_for_testing_linux_x86_64",
})

def _input_output_verification_test_impl(ctx):
    env = analysistest.begin(ctx)
    actions = [a for a in analysistest.target_actions(env) if a.mnemonic == ctx.attr.mnemonic]
    asserts.true(
        env,
        len(actions) == 1,
        "Action not found: %s" % actions,
    )
    package_root = ctx.label.package

    input_files = [paths.join(package_root, a) for a in ctx.attr.input_files]
    output_files = [paths.join(package_root, a) for a in ctx.attr.output_files]

    action = actions[0]

    if len(input_files) > 0:
        expected = sets.make(
            input_files,
        )
        actual = sets.make([
            file.short_path
            for file in action.inputs.to_list()
        ])
        asserts.true(
            env,
            sets.is_subset(expected, actual),
            "Not all input files are present %s %s" % (expected, actual),
        )

    if len(output_files) > 0:
        expected = sets.make(
            output_files,
        )
        actual = sets.make([
            file.short_path
            for file in action.outputs.to_list()
        ])
        asserts.true(
            env,
            sets.is_equal(expected, actual),
            "Not all output files are present %s %s" % (expected, actual),
        )

    return analysistest.end(env)

input_output_verification_test = analysistest.make(
    _input_output_verification_test_impl,
    attrs = {
        "mnemonic": attr.string(),
        "input_files": attr.string_list(),
        "output_files": attr.string_list(),
    },
)
