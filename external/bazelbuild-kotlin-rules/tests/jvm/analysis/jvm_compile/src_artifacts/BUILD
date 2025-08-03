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
load("//kotlin/jvm/testing:jvm_compile_stubs.bzl", "kt_jvm_compile_stubs")

package(
    default_applicable_licenses = ["//:license"],
    default_testonly = True,
)

licenses(["notice"])

kt_jvm_compile_stubs.analysis_test(
    name = "has_allowed_aritfacts_test",
    target_under_test = kt_jvm_compile_stubs.rule(
        name = "has_allowed_artifacts",
        srcs = [
            # go/keep-sorted start
            kt_testing_rules.create_dir(name = "has_allowed_artifacts/java"),
            kt_testing_rules.create_dir(name = "has_allowed_artifacts/kotlin"),
            kt_testing_rules.create_file(name = "has_allowed_artifacts/java.srcjar"),
            kt_testing_rules.create_file(name = "has_allowed_artifacts/src.java"),
            kt_testing_rules.create_file(name = "has_allowed_artifacts/src.kt"),
            # go/keep-sorted end
        ],
    ),
)

kt_testing_rules.assert_failure_test(
    name = "has_unexpected_file_type_test",
    msg_contains = "/src.unexpected",
    target_under_test = kt_jvm_compile_stubs.rule(
        name = "has_unexpected_file_type",
        srcs = [
            kt_testing_rules.create_file(name = "has_unexpected_file_type/src.unexpected"),
        ],
    ),
)

kt_testing_rules.assert_failure_test(
    name = "has_unexpected_dir_ending_test",
    msg_contains = "/kotlin/unexpected",
    target_under_test = kt_jvm_compile_stubs.rule(
        name = "has_unexpected_dir_ending",
        srcs = [
            kt_testing_rules.create_dir(name = "has_unexpected_dir_ending_test/kotlin/unexpected"),
        ],
    ),
)
