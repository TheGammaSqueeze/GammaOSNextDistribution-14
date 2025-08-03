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

load("//kotlin:rules.bzl", "kt_jvm_import", "kt_jvm_library")

package(
    default_visibility = ["//tests/jvm/java/functions:__subpackages__"],
)

licenses(["notice"])

# During coverage builds, every library gets a dep on JaCoCo (Java Code Coverage).
# Libjars, from libraries, only include their direct sources. Together, these behaviours
# trigger an ImportDepsChecker error for :car-jar and :car-inline-jar. To prevent that, we disable
# coverage builds on all downstream targets.
_NO_ZAPFHAHN_BECAUSE_LIBJARS_EXCLUDE_JACOCO = ["nozapfhahn"]

kt_jvm_library(
    name = "car_lib",
    srcs = [
        "Car.kt",
        "CarUtils.kt",
    ],
)

java_import(
    name = "car_lib_import",
    jars = [":libcar_lib.jar"],
    tags = ["incomplete-deps"] + _NO_ZAPFHAHN_BECAUSE_LIBJARS_EXCLUDE_JACOCO,
)

kt_jvm_library(
    name = "car_inline_lib",
    srcs = [
        "CarInlineUtils.kt",
    ],
    visibility = ["//visibility:private"],
    deps = [
        ":car_lib",
    ],
)

kt_jvm_library(
    name = "car_extra_lib",
    srcs = [
        "CarExtraUtils.kt",
    ],
    visibility = ["//visibility:private"],
    deps = [
        ":car_lib",
    ],
)

kt_jvm_import(
    name = "car_inline_and_extra_lib_import",
    jars = [
        ":libcar_inline_lib.jar",
        ":libcar_extra_lib.jar",
    ],
    tags = _NO_ZAPFHAHN_BECAUSE_LIBJARS_EXCLUDE_JACOCO,
    deps = [
        ":car_lib_import",
    ],
)
