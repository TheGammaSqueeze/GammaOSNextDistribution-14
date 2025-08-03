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

load("@bazel_skylib//lib:new_sets.bzl", "sets")
load("@bazel_skylib//lib:paths.bzl", "paths")
load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts")
load("//build/bazel/rules/aidl:aidl_library.bzl", "aidl_library")
load("//build/bazel/rules/cc:cc_aidl_library.bzl", "cc_aidl_library")
load("//build/bazel/rules/test_common:flags.bzl", "action_flags_present_only_for_mnemonic_test")

aidl_library_label_name = "foo_aidl_library"
aidl_files = [
    "a/b/A.aidl",
    "a/b/B.aidl",
]

def _cc_aidl_code_gen_test_impl(ctx):
    env = analysistest.begin(ctx)
    actions = analysistest.target_actions(env)
    asserts.true(
        env,
        len(actions) == 1,
        "expected to have one action per aidl_library target",
    )
    cc_aidl_code_gen_target = analysistest.target_under_test(env)
    action = actions[0]
    argv = action.argv

    # Check inputs are correctly added to command
    aidl_input_path_template = paths.join(
        ctx.genfiles_dir.path,
        ctx.label.package,
        "_virtual_imports",
        aidl_library_label_name,
    ) + "/{}"
    expected_inputs = [
        aidl_input_path_template.format(file)
        for file in aidl_files
    ]
    for expected_input in expected_inputs:
        asserts.true(
            env,
            expected_input in argv,
            "expect {} to be passed to aidl command".format(expected_input),
        )

    # Check generated outputs
    output_path = paths.join(
        ctx.genfiles_dir.path,
        ctx.label.package,
        cc_aidl_code_gen_target.label.name,
    )
    expected_outputs = []
    expected_outputs.extend(
        [
            paths.join(output_path, "a/b/BpA.h"),
            paths.join(output_path, "a/b/BnA.h"),
            paths.join(output_path, "a/b/A.h"),
            paths.join(output_path, "a/b/A.cpp"),
            paths.join(output_path, "a/b/BpB.h"),
            paths.join(output_path, "a/b/BnB.h"),
            paths.join(output_path, "a/b/B.h"),
            paths.join(output_path, "a/b/B.cpp"),
        ],
    )

    asserts.set_equals(
        env,
        sets.make(expected_outputs),
        sets.make([output.path for output in action.outputs.to_list()]),
    )

    # Check the output path is correctly added to includes in CcInfo.compilation_context
    asserts.true(
        env,
        output_path in cc_aidl_code_gen_target[CcInfo].compilation_context.includes.to_list(),
        "output path is added to CcInfo.compilation_context.includes",
    )

    return analysistest.end(env)

cc_aidl_code_gen_test = analysistest.make(
    _cc_aidl_code_gen_test_impl,
)

def _cc_aidl_code_gen_test():
    name = "foo"
    aidl_code_gen_name = name + "_aidl_code_gen"
    code_gen_test_name = aidl_code_gen_name + "_test"

    aidl_library(
        name = aidl_library_label_name,
        srcs = aidl_files,
        tags = ["manual"],
    )
    cc_aidl_library(
        name = name,
        deps = [":foo_aidl_library"],
        tags = ["manual"],
    )
    cc_aidl_code_gen_test(
        name = code_gen_test_name,
        target_under_test = aidl_code_gen_name,
    )

    action_flags_present_test_name = name + "_test_action_flags_present"
    action_flags_present_only_for_mnemonic_test(
        name = action_flags_present_test_name,
        target_under_test = name + "_cpp",
        mnemonics = ["CppCompile"],
        expected_flags = [
            "-DDO_NOT_CHECK_MANUAL_BINDER_INTERFACES",
        ],
    )

    return [
        code_gen_test_name,
        action_flags_present_test_name,
    ]

def _cc_aidl_hash_notfrozen():
    aidl_library_name = "cc_aidl_hash_notfrozen"
    cc_aidl_library_name = aidl_library_name + "cc_aidl_library"
    aidl_code_gen_name = cc_aidl_library_name + "_aidl_code_gen"
    test_name = aidl_code_gen_name + "_test"

    aidl_library(
        name = aidl_library_name,
        srcs = aidl_files,
        tags = ["manual"],
    )
    cc_aidl_library(
        name = cc_aidl_library_name,
        deps = [aidl_library_name],
        tags = ["manual"],
    )
    action_flags_present_only_for_mnemonic_test(
        name = test_name,
        target_under_test = aidl_code_gen_name,
        mnemonics = ["CcAidlCodeGen"],
        expected_flags = ["--hash=notfrozen"],
    )

    return test_name

def _cc_aidl_hash_flag_with_hash_file():
    aidl_library_name = "cc_aidl_hash_flag_with_hash_file"
    cc_aidl_library_name = aidl_library_name + "cc_aidl_library"
    aidl_code_gen_name = cc_aidl_library_name + "_aidl_code_gen"
    test_name = aidl_code_gen_name + "_test"

    aidl_library(
        name = aidl_library_name,
        srcs = aidl_files,
        hash_file = "cc_aidl_hash_flag_with_hash_file/.hash",
        tags = ["manual"],
    )
    cc_aidl_library(
        name = cc_aidl_library_name,
        deps = [aidl_library_name],
        tags = ["manual"],
    )
    action_flags_present_only_for_mnemonic_test(
        name = test_name,
        target_under_test = aidl_code_gen_name,
        mnemonics = ["CcAidlCodeGen"],
        expected_flags = [
            "--hash=$(tail -1 <source file build/bazel/rules/cc/cc_aidl_hash_flag_with_hash_file/.hash>)",
        ],
    )

    return test_name

def cc_aidl_library_test_suite(name):
    native.test_suite(
        name = name,
        tests = [
            _cc_aidl_hash_notfrozen(),
            _cc_aidl_hash_flag_with_hash_file(),
        ] + _cc_aidl_code_gen_test(),
    )
