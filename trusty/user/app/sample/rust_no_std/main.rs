/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

//! Test for `no_std` Rust apps.
//!
//! This test app exists to verify that `no_std` Rust apps build, link, and run
//! correctly. It is also used to test various pieces of functionality that
//! should still work in a `no_std` environment, such as allocation.

#![no_std]
#![feature(start)]

use core::panic::PanicInfo;
use trusty_std::alloc::{FallibleVec, Vec};

#[start]
fn start(_argc: isize, _argv: *const *const u8) -> isize {
    Vec::<u8>::try_with_capacity(128).unwrap();

    let message = b"Hello from no_std Rust!\n";
    unsafe {
        libc::write(libc::STDOUT_FILENO, message.as_ptr().cast(), message.len());
    }

    0
}

#[panic_handler]
fn panic(_panic: &PanicInfo<'_>) -> ! {
    // TODO: This should be `libc::abort`, however `abort` isn't defined in libc
    // when building for Trusty. This should be updated once `abort` is added.
    loop {}
}
