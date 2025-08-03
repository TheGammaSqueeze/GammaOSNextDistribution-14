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

# Description: Kotlin building rules tools
load("//bazel:deploy_jar_freshness_golden_test.bzl", "deploy_jar_freshness_golden_test")
load("//bazel:stubs.bzl", "integration_test_filegroup")

licenses(["notice"])

package(
    default_visibility = ["//visibility:public"],
)

# JAR that contains a marker entry, for identifying apps built by rules_kotlin.
genrule(
    name = "build_marker",
    outs = ["kotlin_build_marker.jar"],
    cmd = """$(location @bazel_tools//tools/zip:zipper) c $@ "assets/_kotlin=" """,
    tools = ["@bazel_tools//tools/zip:zipper"],
)

integration_test_filegroup(
    name = "bazel_osx_p4deps",
)

deploy_jar_freshness_golden_test(
    name = "source_jar_zipper_freshness_test",
    current_jar = "//tools/bin:source_jar_zipper_binary",
    newly_built_jar = "//tools/java/com/google/devtools/kotlin/srczip:source_jar_zipper_deploy.jar",
)
