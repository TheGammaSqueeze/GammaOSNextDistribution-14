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
    expected_friend_jar_names = [],
    target_under_test = ktfa.kt_jvm_library(
        name = "no_kt_exported_friend_cross_package",
        srcs = ["Input.kt"],
        deps = [
            # Exports :friend from this package
            "//tests/jvm/analysis/jvm_library/friends/sub:kt_exports_friend",
        ],
    ),
)

kt_jvm_library_analysis_test(
    name = "has_java_exported_friend_in_package_test",
    expected_friend_jar_names = [
        "libjava_exports_friend-hjar.jar",
        "libfriend-compile.jar",
    ],
    target_under_test = ktfa.kt_jvm_library(
        name = "has_java_exported_friend_in_package",
        srcs = ["Input.kt"],
        deps = [":java_exports_friend"],
    ),
)

kt_jvm_library_analysis_test(
    name = "has_kt_exported_friend_in_package_test",
    expected_friend_jar_names = [
        "libkt_exports_friend-compile.jar",
        "libfriend-compile.jar",
    ],
    target_under_test = ktfa.kt_jvm_library(
        name = "has_kt_exported_friend_in_package",
        srcs = ["Input.kt"],
        deps = [":kt_exports_friend"],
    ),
)

kt_jvm_library_analysis_test(
    name = "no_direct_friend_testing_package_test",
    expected_friend_jar_names = [],
    target_under_test = ktfa.kt_jvm_library(
        name = "no_direct_friend_testing_package",
        srcs = ["Input.kt"],
        deps = ["//tests/jvm/analysis/jvm_library/friends/testing:testingfriend"],
    ),
)

kt_jvm_library_analysis_test(
    name = "no_direct_friend_subpackage_test",
    expected_friend_jar_names = [],
    target_under_test = ktfa.kt_jvm_library(
        name = "no_direct_friend_subpackage",
        srcs = ["Input.kt"],
        deps = ["//tests/jvm/analysis/jvm_library/friends/sub:subfriend"],
    ),
)

kt_jvm_library_analysis_test(
    name = "has_direct_friend_in_package_test",
    expected_friend_jar_names = ["libfriend-compile.jar"],
    target_under_test = ktfa.kt_jvm_library(
        name = "has_direct_friend_in_package",
        srcs = ["Input.kt"],
        deps = [":friend"],
    ),
)

ktfa.java_library(
    name = "java_exports_friend",
    srcs = ["Input.java"],
    exports = [":friend"],
)

ktfa.kt_jvm_library(
    name = "kt_exports_friend",
    srcs = ["Input.kt"],
    exports = [":friend"],
)

ktfa.kt_jvm_library(
    name = "friend",
    srcs = ["Input.kt"],
)
