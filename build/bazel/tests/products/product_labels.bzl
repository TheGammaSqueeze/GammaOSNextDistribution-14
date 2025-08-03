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

"""Product definitions for Android's C/C++ toolchain."""

load("@//build/bazel/tests/products:aosp_arm.variables.bzl", _soong_variables_arm = "variables")
load("@//build/bazel/tests/products:aosp_arm64.variables.bzl", _soong_variables_arm64 = "variables")
load("@//build/bazel/tests/products:aosp_x86.variables.bzl", _soong_variables_x86 = "variables")
load("@//build/bazel/tests/products:aosp_x86_64.variables.bzl", _soong_variables_x86_64 = "variables")
load("@bazel_skylib//lib:dicts.bzl", "dicts")

products = {
    "aosp_arm_for_testing": _soong_variables_arm,
    "aosp_arm64_for_testing": _soong_variables_arm64,
    "aosp_arm64_for_testing_custom_linker_alignment": dicts.add(
        _soong_variables_arm64,
        {"DeviceMaxPageSizeSupported": "16384"},
    ),
    "aosp_arm64_for_testing_no_compression": dicts.add(
        _soong_variables_arm64,
        {"CompressedApex": False},
    ),
    "aosp_arm64_for_testing_unbundled_build": dicts.add(
        _soong_variables_arm64,
        {"Unbundled_build": True},
    ),
    "aosp_arm64_for_testing_with_overrides_and_app_cert": dicts.add(
        _soong_variables_arm64,
        {
            "ManifestPackageNameOverrides": [
                "apex_certificate_label_with_overrides:another",
                "package_name_override_from_config:another.package",
            ],
            "CertificateOverrides": [
                "apex_certificate_label_with_overrides:apex_certificate_label_with_overrides_another_cert",
            ],
            "DefaultAppCertificate": "build/bazel/rules/apex/testdata/devkey",
        },
    ),
    "aosp_arm64_for_testing_memtag_heap_exclude_path": dicts.add(
        _soong_variables_arm64,
        {"MemtagHeapAsyncIncludePaths": ["build/bazel/rules/cc"]},
        {"MemtagHeapExcludePaths": ["build/bazel/rules/cc"]},
    ),
    "aosp_arm64_for_testing_memtag_heap_async_include_path": dicts.add(
        _soong_variables_arm64,
        {"MemtagHeapAsyncIncludePaths": ["build/bazel/rules/cc"]},
    ),
    "aosp_arm64_for_testing_memtag_heap_sync_include_path": dicts.add(
        _soong_variables_arm64,
        {"MemtagHeapSyncIncludePaths": ["build/bazel/rules/cc"]},
    ),
    "aosp_x86_for_testing": _soong_variables_x86,
    "aosp_x86_for_testing_cfi_include_path": dicts.add(
        _soong_variables_x86,
        {"CFIIncludePaths": ["build/bazel/rules/cc"]},
    ),
    "aosp_x86_for_testing_cfi_exclude_path": dicts.add(
        _soong_variables_x86,
        {"CFIExcludePaths": ["build/bazel/rules/cc"]},
    ),
    "aosp_x86_64_for_testing": _soong_variables_x86_64,
    "aosp_x86_64_for_testing_custom_linker_alignment": dicts.add(
        _soong_variables_x86_64,
        {"DeviceMaxPageSizeSupported": "65536"},
    ),
    "aosp_arm64_for_testing_min_sdk_version_override_tiramisu": dicts.add(
        _soong_variables_arm64,
        {"ApexGlobalMinSdkVersionOverride": "Tiramisu"},
    ),
    "aosp_arm64_for_testing_aconfig_release": dicts.add(
        _soong_variables_arm64,
        {"ReleaseAconfigFlagDefaultPermission": "READ_WRITE"},
        {"ReleaseAconfigValueSets": ["//build/bazel/rules/aconfig:aconfig.test.value_set1", "//build/bazel/rules/aconfig:aconfig.test.value_set2"]},
        {"ReleaseVersion": "34"},
    ),
}

product_labels = [
    "@//build/bazel/tests/products:" + name
    for name in products.keys()
]
