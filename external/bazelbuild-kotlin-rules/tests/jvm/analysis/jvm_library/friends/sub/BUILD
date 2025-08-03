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

load("//kotlin/jvm/testing:for_analysis.bzl", ktfa = "kt_for_analysis")
load("//kotlin/jvm/testing:jvm_library_analysis_test.bzl", "kt_jvm_library_analysis_test")

package(
    default_applicable_licenses = ["//:license"],
    default_testonly = True,
    default_visibility = ["//tests/jvm/analysis/jvm_library/friends:__subpackages__"],
)

licenses(["notice"])

kt_jvm_library_analysis_test(
    name = "no_kt_exported_friend_cross_package_test",
    expected_friend_jar_names = [
        "libkt_exports_friend-compile.jar",
        # Absent # "libfriend-compile.jar"
    ],
    target_under_test = ktfa.kt_jvm_library(
        name = "no_kt_exported_friend_cross_package",
        srcs = ["Input.kt"],
        deps = [":kt_exports_friend"],
    ),
)

kt_jvm_library_analysis_test(
    name = "no_direct_friend_cross_package_test",
    expected_friend_jar_names = [],
    target_under_test = ktfa.kt_jvm_library(
        name = "no_direct_friend_cross_package",
        srcs = ["Input.kt"],
        deps = ["//tests/jvm/analysis/jvm_library/friends:friend"],
    ),
)

ktfa.kt_jvm_library(
    name = "kt_exports_friend",
    srcs = ["Input.kt"],
    exports = ["//tests/jvm/analysis/jvm_library/friends:friend"],
)

ktfa.kt_jvm_library(
    name = "subfriend",
    srcs = ["Input.kt"],
)
