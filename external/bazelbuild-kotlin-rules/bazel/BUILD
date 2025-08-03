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

licenses(["notice"])  # Apache 2.0

package(default_visibility = ["//visibility:public"])

java_library(
    name = "auto_service",
    exported_plugins = [":auto_service_plugin"],
    exports = ["@maven//:com_google_auto_service_auto_service_annotations"],
)

java_plugin(
    name = "auto_service_plugin",
    processor_class = "com.google.auto.service.processor.AutoServiceProcessor",
    visibility = ["//visibility:private"],
    deps = ["@maven//:com_google_auto_service_auto_service"],
)

java_plugin(
    name = "auto_value_plugin",
    processor_class = "com.google.auto.value.processor.AutoValueProcessor",
    deps = ["@maven//:com_google_auto_value_auto_value"],
)

java_binary(
    name = "jacoco_cli",
    main_class = "org.jacoco.cli.internal.Main",
    runtime_deps = [
        "@bazel_tools//tools/jdk:JacocoCoverage",
        "@maven//:org_jacoco_org_jacoco_cli",
    ],
)

genrule(
    name = "stub_tool",
    outs = ["stub_tool.sh"],
    cmd = "exit 1",
    executable = True,
)
