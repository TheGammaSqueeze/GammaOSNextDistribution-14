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

load("@bazel_skylib//lib:paths.bzl", "paths")
load("@rules_testing//lib:analysis_test.bzl", "analysis_test", "test_suite")
load("@rules_testing//lib:truth.bzl", "matching")
load("@rules_testing//lib:util.bzl", "util")
load(":framework_resources.bzl", "framework_resources")

def _exists(unused_value):
    return True

_exist_matcher = matching.custom(
    "matcher to check that a set is not empty",
    _exists,
)

def _test_native_providers(name):
    util.helper_target(
        framework_resources,
        name = name + "_subject",
        manifest = "AndroidManifest.xml",
        resource_files = ["res/values/attrs.xml"],
        resource_zips = ["resource_zip.zip"],
    )
    analysis_test(
        name = name,
        impl = _test_native_providers_impl,
        target = name + "_subject",
    )

def _test_starlark_rule(name):
    util.helper_target(
        framework_resources,
        name = name + "_subject",
        manifest = "AndroidManifest.xml",
        resource_files = ["res/values/attrs.xml"],
        resource_zips = ["resource_zip.zip"],
    )
    analysis_test(
        name = name,
        impl = _test_starlark_rule_impl,
        target = name + "_subject" + "_RESOURCES_DO_NOT_USE",
    )

def _test_native_providers_impl(env, target):
    env.expect.that_target(target).default_outputs().contains_predicate(
        matching.file_basename_equals(target.label.name + ".apk"),
    )
    env.expect.that_target(target).output_group("classjar").contains_predicate(_exist_matcher)
    env.expect.that_target(target).output_group("srcjar").contains_predicate(_exist_matcher)
    env.expect.that_target(target).output_group("resource_apk").contains_predicate(_exist_matcher)

def _test_starlark_rule_impl(env, target):
    for mnemonic in [
        "FixAndroidManifest",
        "UnzipResourceZips",
        "CompileAndroidResources",
        "ExcludeDefaultResources",
        "AaptLinkFrameworkRes",
        "FrameworkResSrcJar",
        "StarlarkRClassGenerator",
        "TouchFakeProtoManifest",
    ]:
        # Tautology, but the test will fail if the action doesn't exit.
        env.expect.that_target(target).action_named(mnemonic).mnemonic().equals(mnemonic)

    # Providers
    env.expect.that_target(target).has_provider(AndroidApplicationResourceInfo)
    env.expect.that_target(target).output_group("classjar").contains_predicate(_exist_matcher)
    env.expect.that_target(target).output_group("srcjar").contains_predicate(_exist_matcher)
    env.expect.that_target(target).output_group("resource_apk").contains_predicate(_exist_matcher)

def framework_resources_test_suite(name):
    test_suite(
        name = name,
        tests = [
            _test_native_providers,
            _test_starlark_rule,
        ],
    )
