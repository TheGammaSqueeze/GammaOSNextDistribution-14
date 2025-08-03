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
    default_testonly = 1,
)

licenses(["notice"])

# During coverage builds, every library gets a dep on JaCoCo (Java Code Coverage).
# Libjars, from libraries, only include their direct sources. Together, these behaviours
# trigger an ImportDepsChecker error for :import_a_and_b. To prevent that, we disable
# coverage builds on all downstream targets.
_NO_ZAPFHAHN_BECAUSE_LIBJARS_EXCLUDE_JACOCO = ["nozapfhahn"]

kt_jvm_library(
    name = "A",
    srcs = ["A.kt"],
)

kt_jvm_library(
    name = "B",
    srcs = ["B.kt"],
    deps = [":A"],
)

# This import target with multiple JARs is the main thing we're testing.
kt_jvm_import(
    name = "import_a_and_b",
    jars = [
        ":libA.jar",
        ":libB.jar",
    ],
    srcjar = ":libB-src.jar",
    tags = _NO_ZAPFHAHN_BECAUSE_LIBJARS_EXCLUDE_JACOCO,
)

kt_jvm_library(
    name = "TestRunner",
    srcs = ["TestRunner.kt"],
    tags = _NO_ZAPFHAHN_BECAUSE_LIBJARS_EXCLUDE_JACOCO,
    deps = [":import_a_and_b"],
)

java_test(
    name = "multijarimport_test",
    main_class = "multijarimport.TestRunner",
    tags = _NO_ZAPFHAHN_BECAUSE_LIBJARS_EXCLUDE_JACOCO,
    runtime_deps = [
        ":TestRunner",
    ],
)
