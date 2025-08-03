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

load("//kotlin:rules.bzl", "kt_jvm_library", "kt_jvm_test")

licenses(["notice"])

kt_jvm_library(
    name = "Bar",
    srcs = ["Bar.kt"],
)

kt_jvm_library(
    name = "FooBar",
    srcs = ["FooBar.java"],
    deps = [":Bar"],
)

java_library(
    name = "Qux",
    srcs = ["Qux.java"],
)

kt_jvm_library(
    name = "BazQux",
    srcs = ["BazQux.kt"],
    deps = [":Qux"],
)

kt_jvm_library(
    name = "QuuxCorgeGrault",
    srcs = [
        "Corge.java",
        "QuuxCorge.kt",
        "QuuxCorgeGrault.java",
    ],
)

kt_jvm_test(
    name = "Main",
    srcs = ["Main.kt"],
    main_class = "metasyntactic.Main",
    tags = ["darwin_x86_64_compatible"],
    deps = [
        ":BazQux",
        ":FooBar",
        ":QuuxCorgeGrault",
    ],
)
