# Copyright (C) 2022 The Android Open Source Project
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
load(":bootclasspath.bzl", "bootclasspath")
load(":import.bzl", "java_import")
load(":java_system_modules.bzl", "java_system_modules")

def _bootclasspath_test_impl(ctx):
    env = analysistest.begin(ctx)
    bootclasspath_target = analysistest.target_under_test(env)

    asserts.true(
        env,
        java_common.BootClassPathInfo in bootclasspath_target,
        "Expected BootClassPathInfo in bootclasspath providers.",
    )
    asserts.true(
        env,
        len(bootclasspath_target[java_common.BootClassPathInfo].bootclasspath.to_list()) == 1,
        "Expected bootclasspath to have 1 jars, got %s" % bootclasspath_target[java_common.BootClassPathInfo].bootclasspath,
    )
    return analysistest.end(env)

bootclasspath_test = analysistest.make(
    _bootclasspath_test_impl,
)

def test_bootclasspath_provider():
    name = "test_bootclasspath_provider"
    import_name = name + "_import"
    other_import_name = name + "_other_import"
    system_name = name + "_jsm"
    bootclasspath(
        name = name + "_target",
        bootclasspath = [import_name],
        system = system_name,
        auxiliary = [import_name],
        tags = ["manual"],
    )
    bootclasspath_test(
        name = name,
        target_under_test = name + "_target",
    )
    java_system_modules(
        name = system_name,
        deps = [other_import_name],
        tags = ["manual"],
    )
    java_import(
        name = other_import_name,
        jars = ["a_jar.jar"],
        tags = ["manual"],
    )
    java_import(
        name = import_name,
        jars = ["some_jar.jar"],
        tags = ["manual"],
    )
    return name

def bootclasspath_test_suite(name):
    native.test_suite(
        name = name,
        tests = [
            test_bootclasspath_provider(),
        ],
    )
