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
load("//kotlin/jvm/testing:jvm_library_analysis_test.bzl", "kt_jvm_library_analysis_test")

package(
    default_applicable_licenses = ["//:license"],
    default_testonly = True,
)

licenses(["notice"])

kt_testing_rules.assert_failure_test(
    name = "no_srcs_test",
    msg_contains = "Expected a source-bearing or an export-oriented target",
    target_under_test = ktfa.kt_jvm_library(
        name = "no_srcs",
        srcs = [],
    ),
)

kt_jvm_library_analysis_test(
    name = "no_srcs_with_exports_test",
    expect_jdeps = False,
    required_mnemonic_counts = {"KtAndroidLint": "0"},
    target_under_test = ktfa.kt_jvm_library(
        name = "no_srcs_with_exports",
        srcs = [],
        exports = [
            ktfa.kt_jvm_library(
                name = "no_srcs_with_exports_export",
                srcs = ["Input.kt"],
            ),
        ],
    ),
)

kt_jvm_library_analysis_test(
    name = "only_kt_srcs_test",
    target_under_test = ktfa.kt_jvm_library(
        name = "only_kt_srcs",
        srcs = [
            "Input.kt",
        ],
    ),
)

kt_jvm_library_analysis_test(
    name = "only_java_srcs_test",
    target_under_test = ktfa.kt_jvm_library(
        name = "only_java_srcs",
        srcs = [
            "Input.java",
        ],
    ),
)
