// Copyright 2022, The Android Open Source Project
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

//! Memory layout.

pub mod crosvm;

use crate::console::BASE_ADDRESS;
use crate::linker::__stack_chk_guard;
use aarch64_paging::paging::VirtualAddress;
use core::ops::Range;
use core::ptr::addr_of;

/// First address that can't be translated by a level 1 TTBR0_EL1.
pub const MAX_VIRT_ADDR: usize = 1 << 40;

/// Get an address from a linker-defined symbol.
#[macro_export]
macro_rules! linker_addr {
    ($symbol:ident) => {{
        // SAFETY: We're just getting the address of an extern static symbol provided by the linker,
        // not dereferencing it.
        let addr = unsafe { addr_of!($crate::linker::$symbol) as usize };
        VirtualAddress(addr)
    }};
}

/// Gets the virtual address range between a pair of linker-defined symbols.
#[macro_export]
macro_rules! linker_region {
    ($begin:ident,$end:ident) => {{
        let start = linker_addr!($begin);
        let end = linker_addr!($end);

        start..end
    }};
}

/// Memory reserved for the DTB.
pub fn dtb_range() -> Range<VirtualAddress> {
    linker_region!(dtb_begin, dtb_end)
}

/// Executable code.
pub fn text_range() -> Range<VirtualAddress> {
    linker_region!(text_begin, text_end)
}

/// Read-only data.
pub fn rodata_range() -> Range<VirtualAddress> {
    linker_region!(rodata_begin, rodata_end)
}

/// Initialised writable data.
pub fn data_range() -> Range<VirtualAddress> {
    linker_region!(data_begin, data_end)
}

/// Zero-initialized writable data.
pub fn bss_range() -> Range<VirtualAddress> {
    linker_region!(bss_begin, bss_end)
}

/// Writable data region for the stack.
pub fn stack_range(stack_size: usize) -> Range<VirtualAddress> {
    let end = linker_addr!(init_stack_pointer);
    let start = VirtualAddress(end.0.checked_sub(stack_size).unwrap());
    assert!(start >= linker_addr!(stack_limit));

    start..end
}

/// All writable sections, excluding the stack.
pub fn scratch_range() -> Range<VirtualAddress> {
    linker_region!(eh_stack_limit, bss_end)
}

/// UART console range.
pub fn console_uart_range() -> Range<VirtualAddress> {
    const CONSOLE_LEN: usize = 1; // `uart::Uart` only uses one u8 register.

    VirtualAddress(BASE_ADDRESS)..VirtualAddress(BASE_ADDRESS + CONSOLE_LEN)
}

/// Read-write data (original).
pub fn data_load_address() -> VirtualAddress {
    linker_addr!(data_lma)
}

/// End of the binary image.
pub fn binary_end() -> VirtualAddress {
    linker_addr!(bin_end)
}

/// Value of __stack_chk_guard.
pub fn stack_chk_guard() -> u64 {
    // SAFETY: __stack_chk_guard shouldn't have any mutable aliases unless the stack overflows. If
    // it does, then there could be undefined behaviour all over the program, but we want to at
    // least have a chance at catching it.
    unsafe { addr_of!(__stack_chk_guard).read_volatile() }
}
