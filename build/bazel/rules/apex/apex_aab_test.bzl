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
load(":apex_aab.bzl", "apex_aab")
load(":apex_test_helpers.bzl", "test_apex")

def _apex_aab_test(ctx):
    env = analysistest.begin(ctx)
    target_under_test = analysistest.target_under_test(env)
    asserts.true(
        env,
        len(target_under_test.files.to_list()) == len(ctx.attr.expected_paths),
    )
    for i in range(0, len(ctx.attr.expected_paths)):
        asserts.equals(
            env,
            ctx.attr.expected_paths[i],
            target_under_test.files.to_list()[i].short_path,
        )
    return analysistest.end(env)

apex_aab_test = analysistest.make(
    _apex_aab_test,
    attrs = {
        "expected_paths": attr.string_list(mandatory = True),
    },
)

def _test_apex_aab_generates_aab():
    name = "apex_aab_simple"
    test_name = name + "_test"
    apex_name = name + "_apex"

    test_apex(name = apex_name)

    apex_aab(
        name = name,
        mainline_module = apex_name,
        tags = ["manual"],
    )

    apex_aab_test(
        name = test_name,
        target_under_test = name,
        expected_paths = ["/".join([native.package_name(), apex_name, apex_name + ".aab"])],
    )

    return test_name

def _apex_aab_output_group_test(ctx):
    env = analysistest.begin(ctx)
    target_under_test = analysistest.target_under_test(env)
    actual_paths = sorted([
        f.short_path
        for f in target_under_test[OutputGroupInfo].apex_files.to_list()
    ])
    asserts.equals(
        env,
        sorted(ctx.attr.expected_paths),
        sorted(actual_paths),
    )
    return analysistest.end(env)

apex_aab_output_group_test = analysistest.make(
    _apex_aab_output_group_test,
    attrs = {"expected_paths": attr.string_list(mandatory = True)},
)

def _test_apex_aab_apex_files_output_group():
    name = "apex_aab_apex_files"
    test_name = name + "_test"
    apex_name = name + "_apex"

    test_apex(name = apex_name)

    apex_aab(
        name = name,
        mainline_module = apex_name,
        tags = ["manual"],
    )

    expected_paths = []
    for arch in ["arm", "arm64", "x86", "x86_64", "arm64only", "x86_64only"]:
        paths = [
            "/".join([native.package_name(), "mainline_modules_" + arch, basename])
            for basename in [
                apex_name + ".apex",
                apex_name + "-base.zip",
                "java_apis_used_by_apex/" + apex_name + "_using.xml",
                "ndk_apis_usedby_apex/" + apex_name + "_using.txt",
                "ndk_apis_backedby_apex/" + apex_name + "_backing.txt",
            ]
        ]
        expected_paths.extend(paths)

    apex_aab_output_group_test(
        name = test_name,
        target_under_test = name,
        expected_paths = expected_paths,
    )

    return test_name

def _test_apex_aab_generates_aab_and_apks():
    name = "apex_aab_apks"
    test_name = name + "_test"
    apex_name = name + "_apex"

    test_apex(name = apex_name, package_name = "com.google.android." + apex_name)

    apex_aab(
        name = name,
        mainline_module = apex_name,
        dev_sign_bundle = "//build/make/tools/releasetools:sign_apex",
        dev_keystore = "//build/bazel/rules/apex/testdata:dev-keystore",
        tags = ["manual"],
    )

    apex_aab_test(
        name = test_name,
        target_under_test = name,
        expected_paths = [
            "/".join([native.package_name(), apex_name, apex_name + ".aab"]),
            "/".join([native.package_name(), apex_name, apex_name + ".apks"]),
            "/".join([native.package_name(), apex_name, apex_name + ".cert_info.txt"]),
        ],
    )

    return test_name

def apex_aab_test_suite(name):
    native.test_suite(
        name = name,
        tests = [
            _test_apex_aab_generates_aab(),
            _test_apex_aab_apex_files_output_group(),
            _test_apex_aab_generates_aab_and_apks(),
        ],
    )
