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

licenses(["notice"])

kt_jvm_library(
    name = "internal",
    srcs = ["Internal.kt"],
)

kt_jvm_test(
    name = "InternalTest",
    srcs = ["InternalTest.kt"],
    deps = [
        ":internal",
        "@maven//:com_google_truth_truth",
        "@maven//:junit_junit",
    ],
)

kt_jvm_library(
    name = "internal_test_separate",
    testonly = 1,
    srcs = ["InternalTest.kt"],
    deps = [
        ":internal",
        "@maven//:com_google_truth_truth",
        "@maven//:junit_junit",
    ],
)

java_test(
    name = "InternalTest_separate",
    test_class = "internal.InternalTest",
    runtime_deps = [":internal_test_separate"],
)
