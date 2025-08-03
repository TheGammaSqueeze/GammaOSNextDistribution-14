// Copyright 2023, The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// The compiler looks for this function. It is normally provided by libstd.
// But for baremetal we don't use std and implementation doesn't matter
// anyway. We put a empty place holder here to get compilation pass.
#[no_mangle]
pub extern "C" fn rust_eh_personality() {}

// Provide a few dependencies such as memcpy, memset not provided by RISC-V toolchain
// in the GBL setting
#[link(name = "efi_arch_deps_riscv64", kind = "static")]
extern "C" {
    // Pointer to the EFI header.
    static dos_header: *const core::ffi::c_void;
}

// LLVM doesn't yet have native PE/COFF support for RISC-V target. Thus we have to
// manually add the PE/COFF header to the binary via arch/riscv64/riscv64_efi_header.S.
// Since the source is compiled as a static library, we need to have a public rust
// function that refers to some symbol from it so that the linker doesn't discard it
// from the binary.
#[no_mangle]
pub extern "C" fn get_efi_header() -> *const core::ffi::c_void {
    unsafe { dos_header }
}

// The function is related to stack unwinding and called by liballoc. However we don't expect
// exception handling in UEFI application. If it ever reaches here, panics.
#[no_mangle]
pub extern "C" fn _Unwind_Resume(_: *mut core::ffi::c_void) {
    panic!();
}
