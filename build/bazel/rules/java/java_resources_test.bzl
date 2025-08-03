"""
Copyright (C) 2023 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
"""

load("@rules_testing//lib:analysis_test.bzl", "analysis_test", "test_suite")
load("//build/bazel/rules/java:java_resources.bzl", "java_resources")

def _java_resources_test_impl(env, target):
    target_subject = env.expect.that_target(target)
    target_subject.has_provider(JavaInfo)

def test_java_resources_provider(name):
    res_name = name + "_target"
    java_resources(
        name = res_name,
        resources = ["foo.txt"],
        tags = ["manual"],
    )
    analysis_test(name, impl = _java_resources_test_impl, target = res_name)

def java_resources_test_suite(name):
    test_suite(
        name = name,
        tests = [
            test_java_resources_provider,
        ],
    )
