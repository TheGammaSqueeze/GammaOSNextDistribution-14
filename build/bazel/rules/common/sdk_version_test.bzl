# Copyright (C) 2023 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts")
load("//build/bazel/rules/common:sdk_version.bzl", "sdk_version")
load("//build/bazel/rules/common:api.bzl", "api")

# Warning: this is a *lot* of boilerplate to test just one function.
# Scroll down to sdk_version_test_suite for the actual test cases.

SdkSpec = provider()

def _sdk_spec_from_tester_impl(ctx):
    sdk_spec = sdk_version.sdk_spec_from(ctx.attr.sdk_version)
    return [SdkSpec(kind = sdk_spec.kind, api_level = sdk_spec.api_level)]

sdk_spec_from_tester = rule(
    implementation = _sdk_spec_from_tester_impl,
    attrs = {
        "sdk_version": attr.string(),
    },
)

def _sdk_spec_from_failure_test_impl(ctx):
    env = analysistest.begin(ctx)
    asserts.expect_failure(env, ctx.attr.expected_failure_message)
    return analysistest.end(env)

sdk_spec_from_failure_test = analysistest.make(
    impl = _sdk_spec_from_failure_test_impl,
    expect_failure = True,
    attrs = {"expected_failure_message": attr.string()},
)

def test_sdk_spec_from_failure(name, sdk_version, expected_failure_message = ""):
    sdk_spec_from_tester(
        name = name + "_target",
        sdk_version = sdk_version,
        tags = ["manual"],
    )
    sdk_spec_from_failure_test(
        name = name,
        target_under_test = name + "_target",
        expected_failure_message = expected_failure_message,
    )
    return name

def _sdk_spec_from_output_test_impl(ctx):
    env = analysistest.begin(ctx)
    actual_sdk_spec = analysistest.target_under_test(env)[SdkSpec]
    actual_kind = actual_sdk_spec.kind
    asserts.equals(
        env,
        ctx.attr.expected_kind,
        actual_kind,
        "Expected kind %s, but got %s for sdk version %s" % (
            ctx.attr.expected_kind,
            actual_kind,
            ctx.attr.actual_sdk_version,
        ),
    )

    actual_api_level = actual_sdk_spec.api_level
    asserts.equals(
        env,
        ctx.attr.expected_api_level,
        actual_api_level,
        "Expected api_level %s, but got %s for sdk version %s" % (
            ctx.attr.expected_api_level,
            actual_api_level,
            ctx.attr.actual_sdk_version,
        ),
    )
    return analysistest.end(env)

sdk_spec_from_output_test = analysistest.make(
    impl = _sdk_spec_from_output_test_impl,
    attrs = {
        "actual_sdk_version": attr.string(),
        "expected_kind": attr.string(),
        "expected_api_level": attr.int(),
    },
)

def test_sdk_spec_from_success(name, sdk_version, expected_kind, expected_api_level):
    sdk_spec_from_tester(
        name = name + "_target",
        sdk_version = sdk_version,
        tags = ["manual"],
    )
    sdk_spec_from_output_test(
        name = name,
        target_under_test = name + "_target",
        actual_sdk_version = sdk_version,
        expected_kind = expected_kind,
        expected_api_level = expected_api_level,
    )
    return name

def sdk_version_test_suite(name):
    # sdk version expected to fail to parse.
    failing_sdk_versions = [
        "malformed_malformed",
        "malformed",
        "",
        "core_platform",
    ]
    failure_tests = [
        test_sdk_spec_from_failure(
            name = sdk_version + "_failure_test",
            sdk_version = sdk_version,
        )
        for sdk_version in failing_sdk_versions
    ]

    # Map of sdk_version to expected kind and api_level
    sdk_version_to_kind_and_api_level = {
        "current": ("public", api.FUTURE_API_LEVEL),
        "core_current": ("core", api.FUTURE_API_LEVEL),
        "Tiramisu": ("public", 33),
        "33": ("public", 33),
        "public_33": ("public", 33),
        "none": ("none", api.NONE_API_LEVEL),
        "system_Tiramisu": ("system", 33),
        "system_32": ("system", 32),
    }
    success_tests = [
        test_sdk_spec_from_success(
            name = sdk_version + "_success_test",
            sdk_version = sdk_version,
            expected_kind = sdk_version_to_kind_and_api_level[sdk_version][0],
            expected_api_level = sdk_version_to_kind_and_api_level[sdk_version][1],
        )
        for sdk_version in sdk_version_to_kind_and_api_level.keys()
    ]
    native.test_suite(
        name = name,
        tests = failure_tests + success_tests,
    )
