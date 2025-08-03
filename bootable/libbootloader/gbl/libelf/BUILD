# Copyright (C) 2023 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
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

package(
    default_visibility = ["//visibility:public"],
)

cc_library(
    name = "relocation",
    srcs = ["relocation.cpp"],
    hdrs = ["public/elf/relocation.h"],
    copts = select({
        "@gbl//toolchain:gbl_rust_elf_riscv64": [],
        # For the `elf_static_relocation_checker` host tool, enable
        # print and panics as exit().
        "//conditions:default": ["-DHOST_TOOLING"],
    }),
    includes = ["public"],
    deps = ["@elfutils//:elf_type_header"],
)

cc_test(
    name = "relocation_test",
    size = "small",
    srcs = ["relocation_test.cpp"],
    target_compatible_with = [
        "@platforms//os:linux",
    ],
    deps = [
        ":relocation",
        "@elfutils//:elf_type_header",
        "@googletest//:gtest_main",
    ],
)

cc_binary(
    name = "elf_static_relocation_checker",
    srcs = ["elf_static_relocation_checker.cpp"],
    copts = [
        "-std=c++17",
        "-Wall",
        "-Werror",
    ],
    deps = [
        ":relocation",
        "@elfutils//:elf_type_header",
    ],
)
