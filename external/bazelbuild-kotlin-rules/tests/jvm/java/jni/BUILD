# Copyright 2022 Google LLC. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the License);
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

load("//kotlin:rules.bzl", "kt_jvm_library", "kt_jvm_test")

package(
    default_testonly = 1,
)

licenses(["notice"])

kt_jvm_library(
    name = "NativeApiKt",
    srcs = ["NativeApi.kt"],
    visibility = ["//tests:__subpackages__"],
    runtime_deps = [":jni_NativeApi"],
)

java_library(
    name = "NativeApiJava",
    srcs = ["NativeApi.java"],
    deps = [":libNativeApi.so"],
)

java_library(
    name = "NativeApiJava-through_cc_lib",
    srcs = ["NativeApi.java"],
    deps = [":native_api_so"],
)

kt_jvm_library(
    name = "NativeApiKt-through_cc_lib",
    srcs = ["NativeApi.kt"],
    runtime_deps = [":native_api_so"],
)

cc_library(
    name = "native_api_so",
    srcs = [":libNativeApi.so"],
)

cc_binary(
    name = "libNativeApi.so",
    linkshared = 1,
    linkstatic = 1,
    deps = [":jni_NativeApi"],
)

cc_library(
    name = "jni_NativeApi",
    srcs = ["jni_NativeApi.cc"],
    hdrs = ["jni_NativeApi.h"],  # manually generated with "javac -h NativeApi.java"
    deps = ["@bazel_tools//tools/jdk:jni"],
    alwayslink = 1,
)

[
    kt_jvm_test(
        name = "NativeApiTest_" + native_loader,
        srcs = ["NativeApiTest.kt"],
        args = args,
        main_class = "jni.NativeApiTestKt",
        deps = [
            native_loader,
            "@maven//:com_google_truth_truth",
            "@maven//:junit_junit",
        ],
    )
    for native_loader, args in {
        "NativeApiJava": ["--load"],
        "NativeApiJava-through_cc_lib": ["--load"],
        "NativeApiKt-through_cc_lib": ["--load"],
    }.items()
]

sh_test(
    name = "jdk_check",
    srcs = ["jdk_check.sh"],
)
