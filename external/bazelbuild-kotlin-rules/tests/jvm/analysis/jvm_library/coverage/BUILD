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

kt_testing_rules.coverage_instrumentation_test(
    name = "has_coverage_test",
    expected_instrumented_file_names = [
        "Data.java",
        "Dep.java",
        "Resource.java",
        "RuntimeDep.java",
        "Src.kt",
        "CommonSrc.kt",
    ],
    target_under_test = ktfa.kt_jvm_library(
        name = "has_coverage",
        srcs = ["Src.kt"],
        common_srcs = ["CommonSrc.kt"],
        data = [":data"],
        resources = [":resource"],
        runtime_deps = [":runtime_dep"],
        deps = [":dep"],
    ),
)

ktfa.java_library(
    name = "dep",
    srcs = ["Dep.java"],
)

ktfa.java_library(
    name = "runtime_dep",
    srcs = ["RuntimeDep.java"],
)

ktfa.java_binary(
    name = "data",
    srcs = ["Data.java"],
    main_class = "Data",
)

ktfa.java_binary(
    name = "resource",
    srcs = ["Resource.java"],
    main_class = "Resources",
)
