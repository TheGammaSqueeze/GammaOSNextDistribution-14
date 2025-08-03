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
load(":import.bzl", "java_import")
load(":java_system_modules.bzl", "SystemInfo", "java_system_modules")

def _java_system_modules_test_impl(ctx):
    env = analysistest.begin(ctx)
    java_system_modules_target = analysistest.target_under_test(env)

    asserts.true(
        env,
        java_system_modules_target[SystemInfo].system.is_directory,
        "java_system_modules output should be a directory.",
    )
    asserts.true(
        env,
        len(java_system_modules_target[SystemInfo].java_info.compile_jars.to_list()) > 0,
        "java_system_modules should contain compile jars.",
    )
    return analysistest.end(env)

java_system_modules_test = analysistest.make(
    _java_system_modules_test_impl,
)

def test_java_system_modules_provider():
    name = "test_java_system_modules_provider"
    import_name = name + "_import"
    import_target = ":" + import_name
    java_system_modules(
        name = name + "_target",
        deps = [import_target],
        tags = ["manual"],
    )
    java_system_modules_test(
        name = name,
        target_under_test = name + "_target",
    )

    java_import(
        name = import_name,
        jars = ["some_jar.jar"],
        tags = ["manual"],
    )
    return name

def java_system_modules_test_suite(name):
    native.test_suite(
        name = name,
        tests = [
            test_java_system_modules_provider(),
        ],
    )
