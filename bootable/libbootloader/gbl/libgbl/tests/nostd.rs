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

//! This is a compilation-only test to ensure that libgbl can build against
//! [no_std] code.
//!
//! This also provides a reference for the Rust hooks that a [no_std] user must
//! provide in order to build against libgbl.

#![no_main]
#![no_std]

// use core::panic::PanicInfo;

use gbl as _;

use buddy_system_allocator::LockedHeap;

// Providing allocator to satisfy AVB dependency
#[global_allocator]
static HEAP_ALLOCATOR: LockedHeap<32> = LockedHeap::<32>::new();

static mut HEAP: [u8; 65536] = [0; 65536];

// #[panic_handler]
// fn panic(_: &PanicInfo) -> ! {
//     loop {}
// }

/// main() entry point replacement required by [no_std].
#[no_mangle]
pub fn main() -> ! {
    // SAFETY: Safe because `HEAP` is only used here and `entry` is only called once.
    unsafe {
        // Give the allocator some memory to allocate.
        HEAP_ALLOCATOR.lock().init(HEAP.as_mut_ptr() as usize, HEAP.len());
    }

    panic!()
}
