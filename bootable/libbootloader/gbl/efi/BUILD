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

load("@gbl//toolchain:gbl_toolchain.bzl", "build_with_platform")
load("@gbl_llvm_prebuilts//:info.bzl", "gbl_llvm_tool_path")
load("@rules_rust//rust:defs.bzl", "rust_binary")

package(
    default_visibility = ["//visibility:public"],
)

rust_binary(
    name = "main",
    srcs = [
        "src/android_boot.rs",
        "src/avb.rs",
        "src/error.rs",
        "src/fuchsia_boot.rs",
        "src/main.rs",
        "src/riscv64.rs",
        "src/utils.rs",
    ],
    linker_script = select({
        "@gbl//toolchain:gbl_rust_elf_riscv64": "@gbl//efi/arch/riscv64:riscv64_efi.lds",
        "//conditions:default": None,
    }),
    rustc_flags = [
        "-C",
        "panic=abort",
    ],
    deps = [
        "@avb",
        "@avb//:avb_crypto_ops_sha_impl_staticlib",
        "@gbl//libboot",
        "@gbl//libbootconfig",
        "@gbl//libbootimg",
        "@gbl//libefi",
        "@gbl//libfdt",
        "@gbl//libgbl",
        "@gbl//libstorage",
        "@gbl//third_party/libzbi",
        "@uuid",
        "@zerocopy",
    ] + select({
        "@gbl//toolchain:gbl_rust_elf_riscv64": ["@gbl//efi/arch/riscv64:efi_arch_deps_riscv64"],
        "//conditions:default": [],
    }),
)

genrule(
    name = "gbl_efi",
    srcs = [":main"],
    outs = ["gbl.efi"],
    cmd = select({
        # For RISCV target, existing toolchain can only generate ELF format image.
        # The current solution is to manually add a PE/COFF header at image
        # start and use objcopy to remove the ELF header to make it a PE/COFF image.
        # Also use `elf_static_relocation_checker` to check that our relocation library
        # can handle all the generated relocation types. The following expands to two commands:
        #
        # 1. `llvm-objcopy <elf image> -O binary <efi image>`
        # 2. `elf_static_relocation_checker <elf image> <efi image>`
        "@gbl//toolchain:gbl_rust_elf_riscv64": """
            {} -O binary $(location @gbl//efi:main) $(OUTS) && \\
            $(location @gbl//libelf:elf_static_relocation_checker) $(SRCS) $(OUTS)
        """.format(gbl_llvm_tool_path("llvm-objcopy")),
        "//conditions:default": "cp $(SRCS) $(OUTS)",
    }),
    tools = select({
        "@gbl//toolchain:gbl_rust_elf_riscv64": ["@gbl//libelf:elf_static_relocation_checker"],
        "//conditions:default": [],
    }),
)

build_with_platform(
    name = "x86_64",
    platform = "@gbl//toolchain:gbl_uefi_x86_64",
    deps = [":gbl_efi"],
)

build_with_platform(
    name = "x86_32",
    platform = "@gbl//toolchain:gbl_uefi_x86_32",
    deps = [":gbl_efi"],
)

build_with_platform(
    name = "aarch64",
    platform = "@gbl//toolchain:gbl_uefi_aarch64",
    deps = [":gbl_efi"],
)

build_with_platform(
    name = "riscv64",
    platform = "@gbl//toolchain:gbl_elf_riscv64",
    deps = [":gbl_efi"],
)

filegroup(
    name = "all_platforms",
    srcs = [
        ":aarch64",
        ":riscv64",
        ":x86_32",
        ":x86_64",
    ],
)
