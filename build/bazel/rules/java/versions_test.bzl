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
load("//build/bazel/rules/java:versions.bzl", "java_versions")
load("//build/bazel/rules/common:api.bzl", "api")

def _get_java_version_test_impl(ctx):
    env = unittest.begin(ctx)

    _VERSIONS_UNDER_TEST = {
        (None, api.FUTURE_API_LEVEL): 17,
        (None, 23): 7,
        (None, 33): 11,
        ("1.7", api.FUTURE_API_LEVEL): 7,
        ("1.7", 23): 7,
        ("1.8", 33): 8,
        (None, None): 17,
    }
    for java_sdk_version, expected_java_version in _VERSIONS_UNDER_TEST.items():
        java_version = java_sdk_version[0]
        sdk_version = java_sdk_version[1]
        asserts.equals(env, expected_java_version, java_versions.get_version(java_version, sdk_version), "unexpected java version for java_version %s and sdk_version %s" % (java_version, sdk_version))

    return unittest.end(env)

get_java_version_test = unittest.make(_get_java_version_test_impl)

def versions_test_suite(name):
    unittest.suite(
        name,
        get_java_version_test,
    )
