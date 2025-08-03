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

load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts")
load(":cc_object.bzl", "cc_object")

def _min_sdk_version_target_flag_test_impl(ctx):
    env = analysistest.begin(ctx)
    actions = analysistest.target_actions(env)
    cpp_link_actions = [a for a in actions if a.mnemonic == "CppLink"]

    found = False
    for action in cpp_link_actions:
        for arg in action.argv:
            if arg.startswith("--target="):
                found = True
                asserts.true(
                    env,
                    arg.endswith(ctx.attr.expected_min_sdk_version),
                    "Incorrect --target flag %s. Expected sdk_version %s" % (arg, ctx.attr.expected_min_sdk_version),
                )
    asserts.true(
        env,
        found,
        "No --target flag found in CppLink actions: %s" % (
            [a.argv for a in cpp_link_actions],
        ),
    )

    return analysistest.end(env)

def _create_min_sdk_version_target_flag_test(config_settings = {}):
    return analysistest.make(
        _min_sdk_version_target_flag_test_impl,
        attrs = {
            "expected_min_sdk_version": attr.string(mandatory = True),
        },
        config_settings = config_settings,
    )

_min_sdk_version_target_flag_test = _create_min_sdk_version_target_flag_test()

_apex_min_sdk_version = "25"

_min_sdk_version_target_flag_with_apex_test = _create_min_sdk_version_target_flag_test({
    "@//build/bazel/rules/apex:min_sdk_version": _apex_min_sdk_version,
})

def _crt_cc_object_min_sdk_version_overriden_by_apex_min_sdk_version():
    name = "crt_cc_object_min_sdk_version_overriden_by_apex_min_sdk_version"
    test_name = name + "_test"
    crt_apex_test_name = test_name + "_crt_apex"
    not_crt_apex_test_name = test_name + "_not_crt_apex"
    crt_not_apex_test_name = test_name + "_crt_not_apex"
    not_crt_not_apex_test_name = test_name + "_not_crt_not_apex"
    crt_obj_name = name + "_crt"
    not_crt_obj_name = name + "_not_crt"
    obj_dep_name = name + "_dep"
    obj_min_sdk_version = "16"

    cc_object(
        name = obj_dep_name,
        srcs = ["a.cc"],
        tags = ["manual"],
    )
    cc_object(
        name = crt_obj_name,
        crt = True,
        objs = [obj_dep_name],
        srcs = ["a.cc"],
        min_sdk_version = obj_min_sdk_version,
        tags = ["manual"],
    )
    cc_object(
        name = not_crt_obj_name,
        objs = [obj_dep_name],
        srcs = ["a.cc"],
        min_sdk_version = obj_min_sdk_version,
        tags = ["manual"],
    )
    _min_sdk_version_target_flag_with_apex_test(
        name = crt_apex_test_name,
        target_under_test = crt_obj_name,
        expected_min_sdk_version = _apex_min_sdk_version,
        target_compatible_with = ["@//build/bazel_common_rules/platforms/os:android"],
    )
    _min_sdk_version_target_flag_with_apex_test(
        name = not_crt_apex_test_name,
        target_under_test = not_crt_obj_name,
        expected_min_sdk_version = obj_min_sdk_version,
        target_compatible_with = ["@//build/bazel_common_rules/platforms/os:android"],
    )
    _min_sdk_version_target_flag_test(
        name = crt_not_apex_test_name,
        target_under_test = crt_obj_name,
        expected_min_sdk_version = obj_min_sdk_version,
        target_compatible_with = ["@//build/bazel_common_rules/platforms/os:android"],
    )
    _min_sdk_version_target_flag_test(
        name = not_crt_not_apex_test_name,
        target_under_test = not_crt_obj_name,
        expected_min_sdk_version = obj_min_sdk_version,
        target_compatible_with = ["@//build/bazel_common_rules/platforms/os:android"],
    )

    return [
        crt_apex_test_name,
        not_crt_apex_test_name,
        crt_not_apex_test_name,
        not_crt_not_apex_test_name,
    ]

def _cc_object_partial_linking_test_impl(ctx):
    env = analysistest.begin(ctx)
    actions = analysistest.target_actions(env)
    cpp_link_actions = [a for a in actions if a.mnemonic == "CppLink"]
    noaddrsig_actions = [a for a in actions if a.mnemonic == "CppObjcopyNoAddrsig"]
    asserts.true(
        env,
        len(cpp_link_actions) > 0,
        "No CppLink actions found in : %s" % (
            [a.mnemonic for a in actions],
        ),
    )

    # Verify multiple objects results in a partial link command line
    asserts.true(
        env,
        " ".join(cpp_link_actions[0].argv).find(" -Wl,-r ") != -1,
        "CppLink action missing -r : %s" % (
            cpp_link_actions[0].argv
        ),
    )

    # Verify partial linking also triggers CppObjcopyNoAddrsig
    asserts.true(
        env,
        len(noaddrsig_actions) > 0,
        "No CppObjcopyNoAddrsig actions found in : %s" % (
            actions,
        ),
    )

    return analysistest.end(env)

_cc_object_partial_linking_test = analysistest.make(
    _cc_object_partial_linking_test_impl,
    attrs = {
    },
)

def _cc_object_partial_linking():
    name = "cc_object_partial_linking"
    test_name = name + "_test"

    dep1_name = name + "_dep1"
    dep2_name = name + "_dep2"

    cc_object(
        name = dep1_name,
        srcs = ["a.cc"],
        tags = ["manual"],
    )
    cc_object(
        name = dep2_name,
        srcs = ["b.cc"],
        tags = ["manual"],
    )
    cc_object(
        name = name,
        objs = [dep1_name, dep2_name],
        tags = ["manual"],
    )

    _cc_object_partial_linking_test(
        name = test_name,
        target_under_test = name,
        target_compatible_with = ["@//build/bazel_common_rules/platforms/os:android"],
    )

    return test_name

def cc_object_test_suite(name):
    native.test_suite(
        name = name,
        tests = _crt_cc_object_min_sdk_version_overriden_by_apex_min_sdk_version() + [
            _cc_object_partial_linking(),
        ],
    )
