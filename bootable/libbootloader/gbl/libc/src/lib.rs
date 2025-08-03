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

//! This library provides implementation for a few libc functions for building third party C
//! libraries.

#![no_std]

use core::ptr::null_mut;

/// void *memchr(const void *ptr, int ch, size_t count);
#[no_mangle]
pub extern "C" fn memchr(
    ptr: *const core::ffi::c_void,
    ch: core::ffi::c_int,
    count: core::ffi::c_ulong,
) -> *mut core::ffi::c_void {
    assert!(!ptr.is_null());
    let start = ptr as *const u8;
    let target = (ch & 0xff) as u8;
    for i in 0..count {
        // SAFETY: Buffer is assumed valid and bounded by count.
        let curr = unsafe { start.add(i.try_into().unwrap()) };
        // SAFETY: Buffer is assumed valid and bounded by count.
        if *unsafe { curr.as_ref().unwrap() } == target {
            return curr as *mut _;
        }
    }
    null_mut()
}

/// char *strrchr(const char *str, int c);
#[no_mangle]
pub extern "C" fn strrchr(
    ptr: *const core::ffi::c_char,
    ch: core::ffi::c_int,
) -> *mut core::ffi::c_char {
    assert!(!ptr.is_null());
    // SAFETY: Input is a valid null terminated string.
    let bytes = unsafe { core::ffi::CStr::from_ptr(ptr).to_bytes_with_nul() };
    let target = (ch & 0xff) as u8;
    for c in bytes.iter().rev() {
        if *c == target {
            return c as *const _ as *mut _;
        }
    }
    null_mut()
}

/// size_t strnlen(const char *s, size_t maxlen);
#[no_mangle]
pub fn strnlen(s: *const core::ffi::c_char, maxlen: usize) -> usize {
    match memchr(s as *const _, 0, maxlen.try_into().unwrap()) {
        p if p.is_null() => maxlen,
        p => (p as usize) - (s as usize),
    }
}
