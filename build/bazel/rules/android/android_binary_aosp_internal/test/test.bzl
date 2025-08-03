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

"""Analysis tests for android_binary_aosp_internal."""

load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts")
load("//build/bazel/rules/android/android_binary_aosp_internal:rule.bzl", "android_binary_aosp_internal")
load("//build/bazel/rules/cc:cc_library_shared.bzl", "cc_library_shared")
load("//build/bazel/rules/cc:cc_stub_library.bzl", "cc_stub_library_shared")

def _android_binary_aosp_internal_providers_test_impl(ctx):
    """Basic analysis test that checks android_binary_aosp_internal returns AndroidBinaryNativeLibsInfo.

    Also checks that the provider contains a list of expected .so files.

    Args:
        ctx: The analysis test context

    Returns:
        The analysis test result
    """
    env = analysistest.begin(ctx)
    target = analysistest.target_under_test(env)

    asserts.true(
        env,
        AndroidBinaryNativeLibsInfo in target,
        "AndroidBinaryNativeLibsInfo provider not in the built target.",
    )

    returned_filename_list = []
    for lib_depset in target[AndroidBinaryNativeLibsInfo].native_libs.values():
        returned_filename_list.extend([lib.basename for lib in lib_depset.to_list()])
    returned_filename_list = sorted(returned_filename_list)
    expected_filename_list = sorted(ctx.attr.expected_so_files)

    # Check that the expected list of filenames is exactly the same as the provider's list of filenames.
    asserts.equals(
        env,
        expected_filename_list,
        returned_filename_list,
    )

    return analysistest.end(env)

android_binary_aosp_internal_providers_test = analysistest.make(
    _android_binary_aosp_internal_providers_test_impl,
    attrs = dict(
        expected_so_files = attr.string_list(),
    ),
)

def _test_contains_expected_providers_and_files(name):
    dummy_app_name = name + "_dummy_app_DO_NOT_USE"

    fake_cc_library_shared_with_map_txt = name + "_fake_cc_library_with_map_txt"
    cc_library_shared(
        name = fake_cc_library_shared_with_map_txt,
        stubs_symbol_file = "fake.map.txt",
        tags = ["manual"],
    )
    fake_cc_stub_library_shared = name + "_fake_cc_stub_library"
    cc_stub_library_shared(
        name = fake_cc_stub_library_shared,
        stubs_symbol_file = "fake.map.txt",
        source_library_label = ":" + fake_cc_library_shared_with_map_txt,
        version = "42",
        export_includes = [],
        soname = "libfoo.so",
        deps = [],
        target_compatible_with = [],
        features = [],
        tags = ["manual"],
        api_surface = "module-libapi",
    )
    fake_cc_library_shared = name + "_fake_cc_library_shared"
    cc_library_shared(
        name = fake_cc_library_shared,
        dynamic_deps = [
            ":" + fake_cc_library_shared_with_map_txt,
            ":" + fake_cc_stub_library_shared,
        ],
        tags = ["manual"],
    )

    android_binary_aosp_internal(
        name = dummy_app_name,
        manifest = "AndroidManifest.xml",
        deps = [
            ":" + fake_cc_library_shared,
        ],
        srcs = ["fake.java"],
        tags = ["manual"],
        sdk_version = "current",
    )

    android_binary_aosp_internal_providers_test(
        name = name,
        target_under_test = ":" + dummy_app_name,
        # Only expect libc++ and the non-stub cc libraries.
        expected_so_files = [
            "libc++.so",
            fake_cc_library_shared + ".so",
            fake_cc_library_shared_with_map_txt + ".so",
        ],
    )
    return name

def android_binary_aosp_internal_test_suite(name):
    native.test_suite(
        name = name,
        tests = [
            _test_contains_expected_providers_and_files(name + "_test_contains_expected_providers_and_files"),
        ],
    )
