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

load("//kotlin/common/testing:testing_rules.bzl", "kt_testing_rules")
load("//kotlin/jvm/testing:for_analysis.bzl", ktfa = "kt_for_analysis")
load("//kotlin/jvm/testing:jvm_compile_stubs.bzl", "kt_jvm_compile_stubs")

package(
    default_applicable_licenses = ["//:license"],
    default_testonly = True,
)

licenses(["notice"])

kt_jvm_compile_stubs.analysis_test(
    name = "has_r_java_named_resources_test",
    expected_kotlinc_classpath_names = [
        "platformclasspath.jar",  # bootclasspath
        "libjava_library_resources-hjar.jar",  # r_java
        "libjava_library-hjar.jar",  # deps
        "kotlin-stdlib-ijar.jar",  # stdlibs
        "annotations-13.0-ijar.jar",  # stdlibs
    ],
    target_under_test = kt_jvm_compile_stubs.rule(
        name = "has_r_java_named_resources",
        srcs = ["Input.kt"],
        r_java = ":java_library_resources",
        deps = [":java_library"],
    ),
)

kt_testing_rules.assert_failure_test(
    name = "has_r_java_not_named_resources_test",
    msg_contains = "illegal dependency provided for r_java",
    target_under_test = kt_jvm_compile_stubs.rule(
        name = "has_r_java_not_named_resources",
        srcs = ["Input.kt"],
        r_java = ":java_library",
    ),
)

ktfa.java_library(
    name = "java_library_resources",
    srcs = ["Input.java"],
)

ktfa.java_library(
    name = "java_library",
    srcs = ["Input.java"],
)
