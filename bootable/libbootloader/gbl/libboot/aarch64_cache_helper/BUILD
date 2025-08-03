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

load("@gbl//toolchain:gbl_toolchain.bzl", "link_static_cc_library")

package(
    default_visibility = ["//visibility:public"],
)

exports_files(glob(["**/*"]))

cc_library(
    name = "asm_macros_common",
    hdrs = ["include/common/asm_macros_common.S"],
    includes = ["include"],
)

cc_library(
    name = "aarch64_cache_helper",
    srcs = [
        "disable_cache_mmu.S",
        "@arm_trusted_firmware//:lib/aarch64/cache_helpers.S",
    ],
    hdrs = ["@arm_trusted_firmware//:include/arch/aarch64/asm_macros.S"],
    defines = ["__ASSEMBLER__"],
    deps = [
        ":asm_macros_common",
        "@arm_trusted_firmware//:headers_aarch64",
    ],
)

link_static_cc_library(
    name = "aarch64_cache_helper_staticlib",
    cc_library = ":aarch64_cache_helper",
)
