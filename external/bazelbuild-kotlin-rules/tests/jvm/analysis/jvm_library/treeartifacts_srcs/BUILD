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

kt_jvm_library_analysis_test(
    name = "treeartifact_basename_kotlin_test",
    target_under_test = ktfa.kt_jvm_library(
        name = "treeartifact_basename_kotlin",
        srcs = [
            kt_testing_rules.create_dir(
                name = "treeartifact_extension/kotlin",
                srcs = [
                    "Input.java",  # TODO: Reject this source
                ],
            ),
        ],
    ),
)

kt_jvm_library_analysis_test(
    name = "treeartifact_basename_java_test",
    target_under_test = ktfa.kt_jvm_library(
        name = "treeartifact_basename_java",
        srcs = [
            kt_testing_rules.create_dir(
                name = "treeartifact_extension/java",
                srcs = [
                    "Input.kt",  # TODO: Reject this source
                ],
            ),
        ],
    ),
)

kt_testing_rules.assert_failure_test(
    name = "treeartifact_extension_kt_test",
    msg_contains = "/treeartifact_extension.kt",
    target_under_test = ktfa.kt_jvm_library(
        name = "treeartifact_extension_kt",
        srcs = [
            kt_testing_rules.create_dir(
                name = "treeartifact_extension.kt",
            ),
        ],
    ),
)

kt_testing_rules.assert_failure_test(
    name = "treeartifact_extension_java_test",
    msg_contains = "/treeartifact_extension.java",
    target_under_test = ktfa.kt_jvm_library(
        name = "treeartifact_extension_java",
        srcs = [
            kt_testing_rules.create_dir(
                name = "treeartifact_extension.java",
            ),
        ],
    ),
)

kt_testing_rules.assert_failure_test(
    name = "treeartifact_extension_srcjar_test",
    msg_contains = "/treeartifact_extension.srcjar",
    target_under_test = ktfa.kt_jvm_library(
        name = "treeartifact_extension_srcjar",
        srcs = [
            kt_testing_rules.create_dir(
                name = "treeartifact_extension.srcjar",
            ),
        ],
    ),
)

kt_testing_rules.assert_failure_test(
    name = "treeartifact_no_extension_test",
    msg_contains = "/treeartifact_no_extension",
    target_under_test = ktfa.kt_jvm_library(
        name = "treeartifact_no_extension",
        srcs = [
            kt_testing_rules.create_dir(
                name = "treeartifact_no_extension_dir",
            ),
        ],
    ),
)
