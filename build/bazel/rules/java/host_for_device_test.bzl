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
load(":host_for_device.bzl", "java_host_for_device")
load(":import.bzl", "java_import")

Platform = provider(
    "Platform of the leaf dependency in a linear dependency chain",
    fields = {
        "platform": "the target platform",
        "host_platform": "the host platform",
    },
)

def _host_for_device_tester_aspect_impl(target, ctx):
    if ctx.rule.attr.exports and len(ctx.rule.attr.exports) > 0 and Platform in ctx.rule.attr.exports[0]:
        return ctx.rule.attr.exports[0][Platform]
    return Platform(
        platform = ctx.fragments.platform.platform,
        host_platform = ctx.fragments.platform.host_platform,
    )

host_for_device_tester_aspect = aspect(
    implementation = _host_for_device_tester_aspect_impl,
    attr_aspects = ["exports"],
    fragments = ["platform"],
    provides = [Platform],
)

def _host_for_device_dep_runs_in_exec_config_test_impl(ctx):
    env = analysistest.begin(ctx)
    target_under_test = analysistest.target_under_test(env)
    actual_platform = target_under_test[Platform].platform
    expected_platform = target_under_test[Platform].host_platform
    asserts.equals(env, expected_platform, actual_platform)
    asserts.true(env, JavaInfo in target_under_test, "Expected host_for_device to provide JavaInfo")
    return analysistest.end(env)

host_for_device_dep_runs_in_exec_config_test = analysistest.make(
    _host_for_device_dep_runs_in_exec_config_test_impl,
    extra_target_under_test_aspects = [host_for_device_tester_aspect],
)

def test_host_for_device(name):
    java_host_for_device(
        name = name + "_parent",
        exports = [name + "_child"],
        tags = ["manual"],
    )
    java_import(
        name = name + "_child",
        jars = ["blah.jar"],
        tags = ["manual"],
    )
    host_for_device_dep_runs_in_exec_config_test(
        name = name,
        target_under_test = name + "_parent",
    )
    return name

def host_for_device_test_suite(name):
    native.test_suite(
        name = name,
        tests = [test_host_for_device("test_host_for_device")],
    )
