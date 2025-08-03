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

# Kotlin building rules tools

load("//kotlin:rules.bzl", "kt_jvm_library")

package(
    default_visibility = ["//:internal"],
)

licenses(["notice"])

kt_jvm_library(
    name = "source_jar_zipper_lib",
    srcs = ["SourceJarZipper.kt"],
    deps = ["@maven//:info_picocli_picocli"],
)

java_binary(
    name = "source_jar_zipper",
    main_class = "com.google.devtools.kotlin.srczip.SourceJarZipperKt",
    runtime_deps = [":source_jar_zipper_lib"],
)
