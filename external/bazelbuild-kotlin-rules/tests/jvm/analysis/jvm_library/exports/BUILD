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
)

licenses(["notice"])

kt_jvm_library_analysis_test(
    name = "has_exports_test",
    expected_compile_jar_names = [
        # go/keep-sorted start
        "libexports_transitive-compile.jar",
        "libhas_exports-compile.jar",
        "libjava_library-hjar.jar",
        "libkt_jvm_library-compile.jar",
        "libtransitive-compile.jar",
        # go/keep-sorted end
    ],
    target_under_test = ktfa.kt_jvm_library(
        name = "has_exports",
        srcs = ["Input.kt"],
        exports = [
            ":exports_transitive",
            ":java_library",
            ":kt_jvm_library",
        ],
    ),
)

ktfa.kt_jvm_library(
    name = "exports_transitive",
    srcs = ["Input.kt"],
    exports = [
        ktfa.kt_jvm_library(
            name = "transitive",
            srcs = ["Input.kt"],
        ),
    ],
)

ktfa.kt_jvm_library(
    name = "kt_jvm_library",
    srcs = ["Input.kt"],
)

ktfa.java_library(
    name = "java_library",
    srcs = ["Input.java"],  # need file here so we get a Jar
)
