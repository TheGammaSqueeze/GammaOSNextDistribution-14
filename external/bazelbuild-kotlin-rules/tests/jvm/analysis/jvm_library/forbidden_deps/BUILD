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

package(
    default_applicable_licenses = ["//:license"],
    default_testonly = True,
)

licenses(["notice"])

kt_testing_rules.assert_failure_test(
    name = "has_forbidden_nano_dep_test",
    msg_contains = "fake_nano_proto_lib",
    target_under_test = ktfa.kt_jvm_library(
        name = "has_forbidden_nano_dep",
        srcs = ["Inputs.kt"],
        deps = [":fake_nano_proto_lib"],
    ),
)

kt_testing_rules.assert_failure_test(
    name = "dep_on_has_forbidden_nano_export_test",
    msg_contains = "fake_nano_proto_lib",
    target_under_test = ktfa.kt_jvm_library(
        name = "dep_on_has_forbidden_nano_export",
        srcs = ["Input.kt"],
        deps = [
            ktfa.java_library(
                name = "has_forbidden_nano_export",
                exports = [":fake_nano_proto_lib"],
            ),
        ],
    ),
)

ktfa.java_library(
    name = "fake_nano_proto_lib",
    srcs = [],
    tags = ["nano_proto_library"],
)
