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
load("@gbl_llvm_prebuilts//:info.bzl", "LLVM_PREBUILTS_C_INCLUDE")
load("@rules_rust//bindgen:defs.bzl", "rust_bindgen")
load("@rules_rust//rust:defs.bzl", "rust_library", "rust_test")

package(
    default_visibility = ["//visibility:public"],
)

rust_bindgen(
    name = "libfdt_c_bindgen",
    bindgen_flags = [
        "--ctypes-prefix",
        "core::ffi",
        "--use-core",
        "--with-derive-custom-struct=fdt_header=AsBytes,FromBytes,FromZeroes,PartialEq",
        "--allowlist-function",
        "(fdt_.*)",
        "--allowlist-type",
        "(fdt_.*)",
        "--raw-line",
        """
#![allow(non_camel_case_types)]
#![allow(non_snake_case)]
#![allow(dead_code)]
#![allow(unsafe_op_in_unsafe_fn)]
#![cfg_attr(not(test), no_std)]

use zerocopy::{AsBytes, FromBytes, FromZeroes};
""",
    ],
    cc_lib = "@libfdt_c",
    # For x86_32, we need to explicitly specify 32bit architecture.
    clang_flags = select({
        "@gbl//toolchain:gbl_rust_uefi_x86_32": ["-m32"],
        "//conditions:default": ["-m64"],
    }) + [
        "-I{}".format(LLVM_PREBUILTS_C_INCLUDE),
        "-nostdinc",
    ],
    header = "@libfdt_c//:libfdt.h",
)

genrule(
    name = "bindgen_source",
    srcs = [":libfdt_c_bindgen"],
    outs = ["src/libfdt_c_Def.rs"],
    cmd = "cp $(SRCS) $(OUTS)",
)

rust_library(
    name = "libfdt_c_def",
    srcs = [":bindgen_source"],
    crate_root = ":bindgen_source",
    deps = ["@zerocopy"],
)

rust_library(
    name = "libfdt",
    srcs = ["src/lib.rs"],
    crate_name = "fdt",
    edition = "2021",
    deps = [
        ":libfdt_c_def",
        ":libfdt_c_static",
        "@gbl//libc",
        "@zerocopy",
    ],
)

rust_test(
    name = "libfdt_test",
    compile_data = ["@gbl//libfdt/test:test.dtb"],
    crate = ":libfdt",
)

link_static_cc_library(
    name = "libfdt_c_static",
    cc_library = "@libfdt_c",
)
