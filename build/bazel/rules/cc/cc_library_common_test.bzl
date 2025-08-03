# Copyright (C) 2022 The Android Open Source Project
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

load("@bazel_skylib//lib:unittest.bzl", "analysistest", "unittest", skylib_asserts = "asserts")
load("//build/bazel/rules/test_common:asserts.bzl", roboleaf_asserts = "asserts")
load("//build/bazel/rules/test_common:rules.bzl", "target_under_test_exist_test")
load(":cc_binary.bzl", "cc_binary")
load(":cc_library_common.bzl", "CcAndroidMkInfo", "is_external_directory")
load(":cc_library_shared.bzl", "cc_library_shared")
load(":cc_library_static.bzl", "cc_library_static")
load(":cc_prebuilt_library_shared.bzl", "cc_prebuilt_library_shared")

asserts = skylib_asserts + roboleaf_asserts

def _is_external_directory_test(ctx):
    env = unittest.begin(ctx)

    actual = is_external_directory(ctx.attr.path)

    asserts.equals(env, ctx.attr.expected_value, actual, "expected {path}, to be {external}".format(
        path = ctx.attr.path,
        external = "external" if ctx.attr.expected_value else "non-external",
    ))

    return unittest.end(env)

is_external_directory_test = unittest.make(
    _is_external_directory_test,
    attrs = {
        "path": attr.string(),
        "expected_value": attr.bool(),
    },
)

def _is_external_directory_tests():
    test_cases = {
        "non_external": struct(
            path = "path/to/package",
            expected_value = False,
        ),
        "external": struct(
            path = "external/path/to/package",
            expected_value = True,
        ),
        "hardware": struct(
            path = "hardware/path/to/package",
            expected_value = True,
        ),
        "only_hardware": struct(
            path = "hardware",
            expected_value = True,
        ),
        "hardware_google": struct(
            path = "hardware/google/path/to/package",
            expected_value = False,
        ),
        "hardware_interfaces": struct(
            path = "hardware/interfaces/path/to/package",
            expected_value = False,
        ),
        "hardware_ril": struct(
            path = "hardware/ril/path/to/package",
            expected_value = False,
        ),
        "hardware_libhardware_dir": struct(
            path = "hardware/libhardware/path/to/package",
            expected_value = False,
        ),
        "hardware_libhardware_partial": struct(
            path = "hardware/libhardware_legacy/path/to/package",
            expected_value = False,
        ),
        "vendor": struct(
            path = "vendor/path/to/package",
            expected_value = True,
        ),
        "only_vendor": struct(
            path = "vendor",
            expected_value = True,
        ),
        "vendor_google": struct(
            path = "vendor/google/path/to/package",
            expected_value = False,
        ),
        "vendor_google_with_prefix": struct(
            path = "vendor/pre_google/path/to/package",
            expected_value = False,
        ),
        "vendor_google_with_postfix": struct(
            path = "vendor/google_post/path/to/package",
            expected_value = False,
        ),
    }

    for name, test_case in test_cases.items():
        is_external_directory_test(
            name = name,
            path = test_case.path,
            expected_value = test_case.expected_value,
        )
    return test_cases.keys()

def _target_provides_androidmk_info_test_impl(ctx):
    env = analysistest.begin(ctx)

    target_under_test = analysistest.target_under_test(env)
    mkinfo = target_under_test[CcAndroidMkInfo]
    asserts.list_equals(
        env,
        ctx.attr.expected_static_libs,
        mkinfo.local_static_libs,
        "expected static_libs to be %s, but got %s" % (
            ctx.attr.expected_static_libs,
            mkinfo.local_static_libs,
        ),
    )
    asserts.list_equals(
        env,
        ctx.attr.expected_whole_static_libs,
        mkinfo.local_whole_static_libs,
        "expected whole_static_libs to be %s, but got %s" % (
            ctx.attr.expected_whole_static_libs,
            mkinfo.local_whole_static_libs,
        ),
    )
    asserts.list_equals(
        env,
        ctx.attr.expected_shared_libs,
        mkinfo.local_shared_libs,
        "expected shared_libs to be %s, but got %s" % (
            ctx.attr.expected_shared_libs,
            mkinfo.local_shared_libs,
        ),
    )

    return analysistest.end(env)

target_provides_androidmk_info_test = analysistest.make(
    _target_provides_androidmk_info_test_impl,
    attrs = {
        "expected_static_libs": attr.string_list(),
        "expected_whole_static_libs": attr.string_list(),
        "expected_shared_libs": attr.string_list(),
    },
)

# Same as target_provides_androidmk_info_test, but builds sdk variant of cc_libraries
target_sdk_variant_provides_androidmk_info_test = analysistest.make(
    _target_provides_androidmk_info_test_impl,
    attrs = {
        "expected_static_libs": attr.string_list(),
        "expected_whole_static_libs": attr.string_list(),
        "expected_shared_libs": attr.string_list(),
    },
    config_settings = {
        "@//build/bazel/rules/apex:api_domain": "unbundled_app",
    },
)

def _test_cc_prebuilt_library_shared_is_valid_dynamic_dep():
    name = "cc_prebuilt_library_shared_is_valid_dynamic_dep"
    prebuilt_name = name + "_prebuilt"
    static_name = name + "_static"
    shared_name = name + "_shared"
    binary_name = name + "_binary"
    static_test_name = static_name + "_test"
    shared_test_name = shared_name + "_test"
    binary_test_name = binary_name + "_test"

    cc_prebuilt_library_shared(
        name = prebuilt_name,
        shared_library = "a.so",
        tags = ["manual"],
    )
    cc_library_static(
        name = static_name,
        srcs = ["a.cpp"],
        dynamic_deps = [prebuilt_name],
        tags = ["manual"],
    )
    cc_library_shared(
        name = shared_name,
        srcs = ["a.cpp"],
        dynamic_deps = [prebuilt_name],
        tags = ["manual"],
    )
    cc_binary(
        name = binary_name,
        srcs = ["a.cpp"],
        dynamic_deps = [prebuilt_name],
        tags = ["manual"],
    )

    target_under_test_exist_test(
        name = static_test_name,
        target_under_test = static_name,
    )
    target_under_test_exist_test(
        name = shared_test_name,
        target_under_test = shared_name,
    )
    target_under_test_exist_test(
        name = binary_test_name,
        target_under_test = binary_name,
    )

    return [
        static_test_name,
        shared_test_name,
        binary_test_name,
    ]

def cc_library_common_test_suites(name):
    native.test_suite(
        name = name,
        tests = (
            _is_external_directory_tests() +
            _test_cc_prebuilt_library_shared_is_valid_dynamic_dep()
        ),
    )
