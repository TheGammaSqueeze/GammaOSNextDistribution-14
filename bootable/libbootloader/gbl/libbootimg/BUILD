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
load("@rules_rust//rust:defs.bzl", "rust_library", "rust_test")

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
    name = "bootimg_cc_header",
    srcs = [":bindgen_noop_cc"],
    hdrs = ["@mkbootimg//:include/bootimg/bootimg.h"],
)

# The following genernates rust binding for C definitions in `bootimg.h`. It uses the same
# parameters in script platform/system/tools/mkbootimg/rust/bindgen.sh used for generating the
# checked-in version platform/system/tools/mkbootimg/rust/bootimg_priv.rs.

BLOCKED_TYPES_RE = "__.+|.?int.+"

BLOCKED_ITEMS_RE = "_.+|.?INT.+|PTR.+|ATOMIC.+|.+SOURCE|.+_H|SIG_.+|SIZE_.+|.?CHAR.+"

CUSTOM_STRUCT_RE = "(vendor_)?(boot_img_hdr|ramdisk_table_entry)_v\\d+"

CUSTOM_STRUCT_DERIVES = "AsBytes,FromBytes,FromZeroes,PartialEq,Copy,Clone,Debug"

rust_bindgen(
    name = "bootimg_defs_bindgen",
    bindgen_flags = [
        "--ctypes-prefix",
        "core::ffi",
        "--use-core",
        "--with-derive-default",
        "--blocklist-type={}".format(BLOCKED_TYPES_RE),
        "--blocklist-item={}".format(BLOCKED_ITEMS_RE),
        "--with-derive-custom-struct={}={}".format(CUSTOM_STRUCT_RE, CUSTOM_STRUCT_DERIVES),
        "--raw-line",
        """
#![allow(non_camel_case_types)]
#![allow(non_snake_case)]
#![cfg_attr(not(test), no_std)]
use zerocopy::{AsBytes, FromBytes, FromZeroes};""",
    ],
    cc_lib = ":bootimg_cc_header",
    clang_flags = select({
        # For x86_32, we need to explicitly specify 32bit architecture.
        "@gbl//toolchain:gbl_rust_uefi_x86_32": ["-m32"],
        "//conditions:default": ["-m64"],
    }) + [
        "-x",
        "c++",
        "-nostdinc",
    ],
    header = "@mkbootimg//:include/bootimg/bootimg.h",
)

# The source files do not specify "no_std" and thus can't be used as library crate root in our EFI
# environment. For now, the workaround is to copy over and wrap them under a top level crate that
# enforces no_std.
genrule(
    name = "bootimg_sources_gen",
    srcs = [
        "@mkbootimg//:rust/bootimg.rs",
        ":bootimg_defs_bindgen",
    ],
    outs = [
        "src/bootimg.rs",
        "src/defs.rs",
    ],
    # Copy each src[i] to outs[i]
    cmd = """
        IFS=" " read -a srcs <<< "$(SRCS)" && \
        IFS=" " read -a outs <<< "$(OUTS)" && \
        for index in $${!srcs[@]}; do cp $${srcs[$$index]} $${outs[$$index]}; done
""",
)

rust_library(
    # The naming is expected by "@mkbootimg//:rust/bootimg.rs".
    name = "bootimg_private",
    srcs = ["src/defs.rs"],
    crate_root = "src/defs.rs",
    data = [":bootimg_sources_gen"],
    deps = ["@zerocopy"],
)

rust_library(
    name = "libbootimg",
    srcs = [
        "src/bootimg.rs",
        "src/lib.rs",
    ],
    crate_name = "bootimg",
    data = [":bootimg_sources_gen"],
    deps = [
        ":bootimg_private",
        "@zerocopy",
    ],
)

rust_test(
    name = "libbootimg_test",
    crate = ":libbootimg",
)
