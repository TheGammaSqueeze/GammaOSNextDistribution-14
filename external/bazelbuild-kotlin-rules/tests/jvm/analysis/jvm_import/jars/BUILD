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
load("//kotlin/jvm/testing:for_analysis.bzl", "kt_for_analysis")
load("//kotlin/jvm/testing:jvm_import_analysis_test.bzl", "kt_jvm_import_analysis_test")

package(
    default_applicable_licenses = ["//:license"],
    default_testonly = True,
)

licenses(["notice"])

kt_jvm_import_analysis_test(
    name = "one_jar_test",
    target_under_test = kt_for_analysis.kt_jvm_import(
        name = "one_jar",
        jars = [
            kt_testing_rules.create_file(name = "one_jar.jar"),
        ],
    ),
)

kt_jvm_import_analysis_test(
    name = "many_jar_test",
    target_under_test = kt_for_analysis.kt_jvm_import(
        name = "many_jar",
        jars = [
            kt_testing_rules.create_file(name = "many_jar_0.jar"),
            kt_testing_rules.create_file(name = "many_jar_2.jar"),
        ],
    ),
)
