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
load("@bazel_skylib//lib:unittest.bzl", "asserts", "unittest")
load("//build/bazel/rules/android:manifest_fixer_internal.bzl", manifest_fixer_for_testing = "manifest_fixer_internal")
load("//build/bazel/rules/common:api.bzl", "api")

def _target_sdk_version_override_test_impl(ctx):
    env = unittest.begin(ctx)
    platform_sdk_codename = "Tiramisu"
    platform_sdk_version = "33"
    platform_version_active_codenames = [platform_sdk_codename]

    # Schema: (Input targetSdkVersion, PlatformSdkFinal, Is unbundled app build) -> Expected targetSdkVersion
    _VERSIONS_UNDER_TEST = {
        ("29", False, False): "29",
        ("30", False, True): "30",
        ("current", False, True): str(api.FUTURE_API_LEVEL),
        ("30", True, False): "30",
        ("30", True, True): "30",
        ("Tiramisu", True, True): "33",
        ("current", True, True): "33",
    }
    for (target_sdk_version, platform_sdk_final, is_unbundled_app_build), expected_target_sdk_version in _VERSIONS_UNDER_TEST.items():
        platform_sdk_variables = struct(
            platform_sdk_codename = platform_sdk_codename,
            platform_sdk_final = platform_sdk_final,
            platform_sdk_version = platform_sdk_version,
            platform_version_active_codenames = platform_version_active_codenames,
        )
        asserts.equals(
            env,
            expected_target_sdk_version,
            manifest_fixer_for_testing.target_sdk_version_for_manifest_fixer(
                target_sdk_version,
                platform_sdk_variables,
                is_unbundled_app_build,
            ),
            ("unexpected target SDK version for manifest fixer %s with input target" +
             "SDK version %s, platform SDK variables %s and is_unbundled_app_build %s") % (
                expected_target_sdk_version,
                target_sdk_version,
                platform_sdk_variables,
                is_unbundled_app_build,
            ),
        )
    return unittest.end(env)

target_sdk_version_override_test = unittest.make(_target_sdk_version_override_test_impl)

def manifest_fixer_test_suite(name):
    unittest.suite(
        name,
        target_sdk_version_override_test,
    )
