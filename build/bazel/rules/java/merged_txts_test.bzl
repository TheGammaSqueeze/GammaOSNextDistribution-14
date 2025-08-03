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

load("@bazel_skylib//lib:paths.bzl", "paths")
load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts")
load(":merged_txts.bzl", "merged_txts")
load(":sdk_library.bzl", "java_sdk_library")

SCOPE_TO_JAVA_SDK_LIBRARY_FILE = {
    "public": "sdk_public.txt",
    "system": "sdk_system.txt",
    "module-lib": "sdk_module_lib.txt",
    "system-server": "sdk_system_server.txt",
}

def _basic_merged_txts_test_impl(ctx):
    env = analysistest.begin(ctx)
    actions = analysistest.target_actions(env)

    base_file = paths.join(paths.dirname(ctx.build_file_path), ctx.attr.base)
    asserts.true(
        env,
        base_file in actions[0].argv,
        "Base file {} of scope {} is not in args list".format(base_file, ctx.attr.scope),
    )

    java_sdk_library_file = paths.join(
        paths.dirname(ctx.build_file_path),
        SCOPE_TO_JAVA_SDK_LIBRARY_FILE[ctx.attr.scope],
    )
    asserts.true(
        env,
        java_sdk_library_file in actions[0].argv,
        "java_sdk_library file {} of scope {} is not in args list".format(java_sdk_library_file, ctx.attr.scope),
    )

    return analysistest.end(env)

basic_merged_txts_test = analysistest.make(
    _basic_merged_txts_test_impl,
    attrs = {
        "scope": attr.string(),
        "base": attr.string(),
    },
)

def test_generated_current_txt():
    name = "generated_current_txt_test"
    target_name = name + "_target"
    scope = "public"
    base = "non-updatable-current.txt"
    merged_txts(
        name = target_name,
        scope = scope,
        base = base,
        deps = ["dep"],
        tags = ["manual"],
    )
    java_sdk_library(
        name = "dep",
        public = SCOPE_TO_JAVA_SDK_LIBRARY_FILE["public"],
        system = SCOPE_TO_JAVA_SDK_LIBRARY_FILE["system"],
        module_lib = SCOPE_TO_JAVA_SDK_LIBRARY_FILE["module-lib"],
        system_server = SCOPE_TO_JAVA_SDK_LIBRARY_FILE["system-server"],
    )
    basic_merged_txts_test(
        name = name,
        target_under_test = target_name,
        scope = scope,
        base = base,
    )
    return name

def test_generated_system_current_txt():
    name = "generated_system_current_txt_test"
    target_name = name + "_target"
    scope = "system"
    base = "non-updatable-system-current.txt"
    merged_txts(
        name = target_name,
        scope = scope,
        base = base,
        deps = ["dep"],
        tags = ["manual"],
    )
    basic_merged_txts_test(
        name = name,
        target_under_test = target_name,
        scope = scope,
        base = base,
    )
    return name

def test_generated_module_lib_current_txt():
    name = "generated_module_lib_current_txt_test"
    target_name = name + "_target"
    scope = "module-lib"
    base = "non-updatable-module-lib_current.txt"
    merged_txts(
        name = target_name,
        scope = scope,
        base = base,
        deps = ["dep"],
        tags = ["manual"],
    )
    basic_merged_txts_test(
        name = name,
        target_under_test = target_name,
        scope = scope,
        base = base,
    )
    return name

def test_generated_system_server_current_txt():
    name = "generated_system_server_current_txt_test"
    target_name = name + "_target"
    scope = "system-server"
    base = "non-updatable-system-server-current.txt"
    merged_txts(
        name = target_name,
        scope = scope,
        base = base,
        deps = ["dep"],
        tags = ["manual"],
    )
    basic_merged_txts_test(
        name = name,
        target_under_test = target_name,
        scope = scope,
        base = base,
    )
    return name

def merged_txts_test_suite(name):
    native.test_suite(
        name = name,
        tests = [
            test_generated_current_txt(),
            test_generated_system_current_txt(),
            test_generated_module_lib_current_txt(),
            test_generated_system_server_current_txt(),
        ],
    )
