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

load("@rules_rust//bindgen:defs.bzl", "rust_bindgen")
load("@rules_rust//rust:defs.bzl", "rust_library")

package(
    default_visibility = ["//visibility:public"],
)

# Newer version of `rust_bindgen` requires a `cc_library` target that actually produces a static
# library and instead of only headers. Thus we generate a placeholder source file to meet the
# requirement.
genrule(
    name = "bindgen_noop_cc",
    outs = ["bindgen_noop_cc.cc"],
    cmd = "touch $(OUTS)",
)

cc_library(
    name = "bindgen_cc_lib",
    srcs = [":bindgen_noop_cc"],
    deps = ["@linux_x86_64_sysroot//:linux_x86_64_sysroot_include"],
)

CUSTOM_DERIVES = "AsBytes,FromBytes,FromZeroes"

rust_bindgen(
    name = "x86_bootparam_bindgen",
    bindgen_flags = [
        "--ctypes-prefix",
        "core::ffi",
        "--use-core",
        "--allowlist-type",
        "boot_params",
        "--with-derive-default",
        "--with-derive-custom-struct=.*={}".format(CUSTOM_DERIVES),
        "--with-derive-custom-union=.*={}".format(CUSTOM_DERIVES),
        "--raw-line",
        """
#![allow(non_camel_case_types)]
#![allow(non_snake_case)]
#![cfg_attr(not(test), no_std)]
use zerocopy::{AsBytes, FromBytes, FromZeroes};""",
    ],
    cc_lib = ":bindgen_cc_lib",
    header = "@linux_x86_64_sysroot//:sysroot/usr/include/x86_64-linux-gnu/asm/bootparam.h",
)

rust_library(
    name = "x86_bootparam_defs",
    srcs = [":x86_bootparam_bindgen"],
    crate_root = ":x86_bootparam_bindgen",
    data = [":x86_bootparam_bindgen"],
    deps = ["@zerocopy"],
)

rust_library(
    name = "libboot",
    srcs = glob(["**/*.rs"]),
    crate_name = "boot",
    edition = "2021",
    deps = [
        ":x86_bootparam_defs",
        "@zerocopy",
    ] + select({
        "@gbl//toolchain:gbl_rust_uefi_aarch64": [
            "@gbl//libboot/aarch64_cache_helper:aarch64_cache_helper_staticlib",
        ],
        "//conditions:default": [],
    }),
)
