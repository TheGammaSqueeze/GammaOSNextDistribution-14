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

load("//kotlin:rules.bzl", "kt_jvm_library")

licenses(["notice"])

package(
    default_testonly = 1,
)

java_test(
    name = "ijar",
    main_class = "ijar.MainKt",
    runtime_deps = [":main"],
)

java_test(
    name = "suspense",
    test_class = "ijar.SuspendTest",
    runtime_deps = [":SuspendTest"],
)

kt_jvm_library(
    name = "inline",
    srcs = [
        "InlineCapture.kt",
        "InlineInnerClass.kt",
        "ReifiedInline.kt",
    ],
)

# Compiling this file by itself triggers synthetic methods needed when compiling
# dependants.  These methods carry annotations.
kt_jvm_library(
    name = "statics",
    srcs = ["JvmStatics.kt"],
)

kt_jvm_library(
    name = "main",
    srcs = ["Main.kt"],
    deps = [
        ":inline",
        ":statics",
    ],
)

kt_jvm_library(
    name = "inlined_suspense",
    srcs = ["DoubleInline.kt"],
)

kt_jvm_library(
    name = "SuspendTest",
    srcs = ["SuspendTest.kt"],
    deps = [
        ":inlined_suspense",
        "@maven//:com_google_truth_truth",
        "@maven//:junit_junit",
    ],
)

java_library(
    name = "SamInJava",
    srcs = ["SamInJava.java"],
)

kt_jvm_library(
    name = "SamExt",
    srcs = ["SamExt.kt"],
    deps = [
        ":SamInJava",
    ],
)

kt_jvm_library(
    name = "SamExtUser",
    srcs = ["SamExtUser.kt"],
    deps = [
        ":SamExt",
        ":SamInJava",
    ],
)

java_test(
    name = "inlinedSamExt",
    main_class = "ijar.SamExtUserKt",
    runtime_deps = [
        ":SamExtUser",
    ],
)

kt_jvm_library(
    name = "InlineWhenMapping",
    srcs = ["InlineWhenMapping.kt"],
)

kt_jvm_library(
    name = "InlineWhenMappingUser",
    srcs = ["InlineWhenMappingUser.kt"],
    deps = [":InlineWhenMapping"],
)

java_test(
    name = "inlineWhenMapping",
    main_class = "ijar.InlineWhenMappingUserKt",
    runtime_deps = [
        ":InlineWhenMappingUser",
    ],
)
