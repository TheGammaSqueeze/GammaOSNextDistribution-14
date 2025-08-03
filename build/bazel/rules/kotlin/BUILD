# Copyright (C) 2023 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

load("@rules_kotlin//toolchains/kotlin_jvm:kt_jvm_toolchains.bzl", "kt_jvm_toolchain", "kt_jvm_toolchains")
load("//build/bazel/rules/java:versions.bzl", "java_versions")

kt_jvm_version_select_dict = {
    "//build/bazel/rules/java:host_" + setting: [java_versions.kt_jvm_version_from_normalized_java_version(version)]
    for version, setting in java_versions.VERSION_TO_CONFIG_SETTING.items()
} | {
    "//conditions:default": [java_versions.kt_jvm_version_from_normalized_java_version(java_versions.get_version())],
}

kt_jvm_toolchain(
    name = "kt_jvm_toolchain_linux_jdk",
    android_java8_apis_desugared = False,
    kotlinc_cli_flags = ["-jvm-target"] + select(kt_jvm_version_select_dict),
    kotlinc_ide_flags = ["-jvm-target"] + select(kt_jvm_version_select_dict),
    visibility = ["//visibility:public"],
)

toolchain(
    name = "kt_jvm_toolchain_linux",
    exec_compatible_with = [
        "@platforms//os:linux",
    ],
    toolchain = ":kt_jvm_toolchain_linux_jdk",
    toolchain_type = kt_jvm_toolchains.type,
    visibility = ["//visibility:public"],
)
