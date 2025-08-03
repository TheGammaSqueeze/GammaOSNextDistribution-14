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

"""
This file contains rules and logic for setting up GBL workspace dependencies in the AOSP
u-boot-mainline branch.
"""

load("@bazel_tools//tools/build_defs/repo:utils.bzl", "maybe")
load("@gbl//toolchain:gbl_workspace_util.bzl", "android_rust_prebuilts", "gbl_llvm_prebuilts")
load("@kernel_toolchain_info//:dict.bzl", "CLANG_VERSION")

def rust_crate_build_file(name, deps = [], features = [], rustc_flags = []):
    """Generate BUILD file content for a rust crate

    This helper is suitable for crates that have straightforward build rules. Specifically, the
    crate contains a single `rust_library` targets that includes all source files under the repo.
    There is not any need of preprocessing, patching or source generation.

    Args:
        name (String): name of the rust_library target.
        deps (List of strings): The `deps` field.
        features (List of strings): The `features` field.
        rustc_flags (List of strings): The `rustc_flags` field.

    Returns:
        A string for the BUILD file content.
    """
    deps = "[{}]".format(",".join(["\"{}\"".format(ele) for ele in deps]))
    features = "[{}]".format(",".join(["\"{}\"".format(ele) for ele in features]))
    rustc_flags = "[{}]".format(",".join(["\"{}\"".format(ele) for ele in rustc_flags]))
    return """
load("@rules_rust//rust:defs.bzl", "rust_library")

rust_library(
    name = \"{}\",
    srcs = glob(["**/*.rs"]),
    crate_features = {},
    edition = "2021",
    rustc_flags ={},
    visibility = ["//visibility:public"],
    deps = {},
)
""".format(name, features, rustc_flags, deps)

def define_gbl_workspace(name = None):
    """Set up worksapce dependencies for GBL

    Dependencies are checked out during "repo init". The rule simply maps them to the correct repo
    names.

    Args:
        name (String): Placeholder for buildifier check.
    """
    maybe(
        repo_rule = native.local_repository,
        name = "rules_rust",
        path = "external/bazelbuild-rules_rust",
    )

    maybe(
        repo_rule = native.local_repository,
        name = "rules_license",
        path = "external/bazelbuild-rules_license",
    )

    native.new_local_repository(
        name = "rules_rust_tinyjson",
        path = "external/rust/crates/tinyjson",
        build_file = "@rules_rust//util/process_wrapper:BUILD.tinyjson.bazel",
    )

    native.new_local_repository(
        name = "llvm_linux_x86_64_prebuilts",
        path = "prebuilts/clang/host/linux-x86/clang-{}".format(CLANG_VERSION),
        build_file_content = "",
    )

    native.new_local_repository(
        name = "linux_x86_64_sysroot",
        path = "build/kernel/build-tools",
        build_file_content = """exports_files(glob(["**/*"]))
cc_library(
    name = "linux_x86_64_sysroot_include",
    hdrs = glob(["sysroot/usr/include/**/*.h"]),
    includes = [ "sysroot/usr/include" ],
    visibility = ["//visibility:public"],
)
""",
    )

    android_rust_prebuilts(
        name = "rust_prebuilts",
        path = "prebuilts/rust/",
        build_file = "@gbl//toolchain:BUILD.android_rust_prebuilts.bazel",
    )

    native.new_local_repository(
        name = "bindgen",
        path = "prebuilts/clang-tools/linux-x86/bin",
        build_file_content = """exports_files(["bindgen"])""",
    )

    native.new_local_repository(
        name = "elfutils",
        path = "external/elfutils",
        build_file_content = """
cc_library(
    name = "elf_type_header",
    hdrs = ["libelf/elf.h"],
    visibility = ["//visibility:public"],
)
""",
    )

    native.new_local_repository(
        name = "mkbootimg",
        path = "tools/mkbootimg",
        build_file_content = """exports_files(glob(["**/*"]))""",
    )

    native.new_local_repository(
        name = "libfdt_c",
        path = "external/dtc/libfdt",
        build_file = "@gbl//libfdt:BUILD.libfdt_c.bazel",
    )

    native.new_local_repository(
        name = "arm_trusted_firmware",
        path = "external/arm-trusted-firmware",
        build_file = "@gbl//libboot/aarch64_cache_helper:BUILD.arm_trusted_firmware.bazel",
    )

    native.new_local_repository(
        name = "avb",
        path = "external/avb",
        build_file = "@gbl//libavb:BUILD.avb.bazel",
    )

    native.new_local_repository(
        name = "uuid",
        path = "external/rust/crates/uuid",
        build_file_content = rust_crate_build_file("uuid"),
    )

    native.new_local_repository(
        name = "cstr",
        path = "packages/modules/Virtualization/libs/cstr",
        build_file_content = rust_crate_build_file("cstr"),
    )

    native.new_local_repository(
        name = "spin",
        path = "external/rust/crates/spin",
        build_file_content = rust_crate_build_file(
            "spin",
            features = [
                "mutex",
                "spin_mutex",
            ],
            rustc_flags = [
                "-A",
                "unused_imports",
            ],
        ),
    )

    native.new_local_repository(
        name = "static_assertions",
        path = "external/rust/crates/static_assertions",
        build_file_content = rust_crate_build_file("static_assertions"),
    )

    # Following are third party rust crates dependencies.

    THIRD_PARTY_CRATES = [
        "bitflags",
        "byteorder",
        "cfg-if",
        "crc32fast",
        "hex",
        "proc-macro2",
        "quote",
        "syn",
        "unicode-ident",
        "zerocopy",
        "zerocopy-derive",
    ]

    for crate in THIRD_PARTY_CRATES:
        native.new_local_repository(
            name = crate,
            path = "external/rust/crates/{}".format(crate),
            build_file = "//external/rust/crates/{}:BUILD".format(crate),
        )

    # Set up a repo to export LLVM tool/library/header/sysroot paths
    gbl_llvm_prebuilts(name = "gbl_llvm_prebuilts")

    # We don't register GBL toolchains here because they will be masked by toolchains from
    # `build/kleaf//:` as they are registered earlier. Instead, we will pass GBL toolchains via
    # bazel commandline argument "--extra_toolchains=@gbl//toolchain:all" when building GBL
    # targets, which allows them to be evaluated first during toolchain resolution.
