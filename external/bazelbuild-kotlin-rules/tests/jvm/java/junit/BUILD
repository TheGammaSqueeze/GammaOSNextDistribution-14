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
    name = "junit",
    srcs = glob(["*.kt"]),
    deps = [
        "@maven//:com_google_truth_truth",
        "@maven//:junit_junit",
    ],
)

# test_class inferred from test name (srcs irrelevant)
kt_jvm_test(
    name = "FooTest",
    srcs = ["FooTest.kt"],
    tags = ["darwin_x86_64_compatible"],
    deps = [
        "@maven//:com_google_truth_truth",
        "@maven//:junit_junit",
    ],
)

kt_jvm_test(
    name = "FooTest_separate",
    tags = ["darwin_x86_64_compatible"],
    test_class = "junit.FooTest",
    runtime_deps = [":junit"],
)

# test_class inferred from test name (srcs not needed)
kt_jvm_test(
    name = "BarTest",
    tags = ["darwin_x86_64_compatible"],
    runtime_deps = [":junit"],
)

java_test(
    name = "SampleTest",
    tags = ["darwin_x86_64_compatible"],
    runtime_deps = [":junit"],
)
