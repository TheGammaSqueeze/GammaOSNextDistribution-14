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

"""kt_for_analysis"""

load("//:visibility.bzl", "RULES_KOTLIN")
load("//kotlin:compiler_plugin.bzl", "kt_compiler_plugin")
load("//kotlin:jvm_import.bzl", "kt_jvm_import")
load("//kotlin:jvm_library.bzl", "kt_jvm_library")
load("//kotlin/common/testing:testing_rules.bzl", "kt_testing_rules")

visibility(RULES_KOTLIN)

kt_for_analysis = struct(
    # go/keep-sorted start
    java_binary = kt_testing_rules.wrap_for_analysis(native.java_binary),
    java_import = kt_testing_rules.wrap_for_analysis(native.java_import),
    java_library = kt_testing_rules.wrap_for_analysis(native.java_library),
    java_plugin = kt_testing_rules.wrap_for_analysis(native.java_plugin),
    kt_compiler_plugin = kt_testing_rules.wrap_for_analysis(kt_compiler_plugin),
    kt_jvm_import = kt_testing_rules.wrap_for_analysis(kt_jvm_import),
    kt_jvm_library = kt_testing_rules.wrap_for_analysis(kt_jvm_library),
    # go/keep-sorted end
)
