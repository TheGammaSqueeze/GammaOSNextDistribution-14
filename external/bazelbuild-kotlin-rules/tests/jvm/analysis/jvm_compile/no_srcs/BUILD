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

load("//kotlin:common.bzl", "common")
load("//kotlin/common/testing:testing_rules.bzl", "kt_testing_rules")
load("//kotlin/jvm/testing:for_analysis.bzl", ktfa = "kt_for_analysis")
load("//kotlin/jvm/testing:jvm_compile_stubs.bzl", "kt_jvm_compile_stubs")

package(
    default_applicable_licenses = ["//:license"],
    default_testonly = True,
)

licenses(["notice"])

kt_jvm_compile_stubs.analysis_test(
    name = "no_srcs_android_family_test",
    target_under_test = kt_jvm_compile_stubs.rule(
        name = "no_srcs_android_family",
        srcs = [],
        common_srcs = [],
        rule_family = common.RULE_FAMILY.ANDROID_LIBRARY,
    ),
)

kt_testing_rules.assert_failure_test(
    name = "no_srcs_jvm_family_test",
    msg_contains = "Expected one of (srcs, common_srcs, exports) is not empty",
    target_under_test = kt_jvm_compile_stubs.rule(
        name = "no_srcs_jvm_family",
        srcs = [],
        common_srcs = [],
        rule_family = common.RULE_FAMILY.JVM_LIBRARY,
        exports = [],
    ),
)

kt_jvm_compile_stubs.analysis_test(
    name = "no_srcs_jvm_family_with_exports_test",
    target_under_test = kt_jvm_compile_stubs.rule(
        name = "no_srcs_jvm_family_with_exports",
        srcs = [],
        common_srcs = [],
        rule_family = common.RULE_FAMILY.ANDROID_LIBRARY,
        exports = [
            ktfa.java_library(
                name = "no_srcs_jvm_family_export",
                srcs = [],
            ),
        ],
    ),
)
